package app.masary.feature.questionsession.data

import app.masary.core.datastore.SessionManager
import app.masary.core.local.ConfirmedProfileDelta
import app.masary.core.local.ConfirmedStudentDelta
import app.masary.core.local.ConfirmedSubjectDelta
import app.masary.core.local.PendingOperationEntity
import app.masary.core.local.QuestionAnswerEntity
import app.masary.core.local.QuestionSessionEntity
import app.masary.core.local.StudentLocalStore
import app.masary.core.models.auth.AuthTokens
import app.masary.core.network.auth.StudentAuthApi
import app.masary.core.network.auth.StudentRefreshRequestDto
import app.masary.core.network.question.QuestionAnswerRequestDto
import app.masary.core.network.question.QuestionConfirmedDeltaDto
import app.masary.core.network.question.QuestionConfirmedProfileDeltaDto
import app.masary.core.network.question.QuestionConfirmedSubjectDeltaDto
import app.masary.core.network.question.QuestionFinishRequestDto
import app.masary.core.network.question.QuestionFinishResultDto
import app.masary.core.network.question.QuestionActiveTimePolicyDto
import app.masary.core.network.question.QuestionResultPolicyDto
import app.masary.core.network.question.QuestionSessionPackageDataDto
import app.masary.core.network.question.QuestionSessionPolicyDto
import app.masary.core.network.question.QuestionSessionQuestionDto
import app.masary.core.network.question.StudentQuestionSessionApi
import app.masary.feature.questionsession.domain.ConnectItem
import app.masary.feature.questionsession.domain.ConnectAnswerPair
import app.masary.feature.questionsession.domain.QuestionAnswerInput
import app.masary.feature.questionsession.domain.QuestionAnswerSyncSummary
import app.masary.feature.questionsession.domain.QuestionItem
import app.masary.feature.questionsession.domain.QuestionOption
import app.masary.feature.questionsession.domain.QuestionPayload
import app.masary.feature.questionsession.domain.QuestionSessionException
import app.masary.feature.questionsession.domain.QuestionSessionExpiredException
import app.masary.feature.questionsession.domain.QuestionActiveTimePolicy
import app.masary.feature.questionsession.domain.QuestionResultPolicy
import app.masary.feature.questionsession.domain.QuestionSessionInfo
import app.masary.feature.questionsession.domain.QuestionSessionNetworkException
import app.masary.feature.questionsession.domain.QuestionSessionNotFoundException
import app.masary.feature.questionsession.domain.QuestionSessionPackage
import app.masary.feature.questionsession.domain.QuestionSessionPolicy
import app.masary.feature.questionsession.domain.QuestionSessionProgress
import app.masary.feature.questionsession.domain.QuestionSessionRepository
import app.masary.feature.questionsession.domain.QuestionSessionResult
import app.masary.feature.questionsession.domain.QuestionSessionScore
import app.masary.feature.questionsession.domain.QuestionSessionServiceException
import app.masary.feature.questionsession.domain.QuestionSessionSourceUnavailableException
import app.masary.feature.questionsession.domain.QuestionType
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

private const val QUESTION_RESULT_DOCUMENT_KIND = "question_session_result"
private const val QUESTION_ACTIVE_TIME_DOCUMENT_KIND = "question_session_active_time"

class NetworkQuestionSessionRepository(
    private val questionApi: StudentQuestionSessionApi,
    private val authApi: StudentAuthApi,
    private val sessionManager: SessionManager,
    private val localStore: StudentLocalStore,
    private val onPendingAnswerSaved: () -> Unit = {},
    private val gson: Gson = Gson(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : QuestionSessionRepository {
    override suspend fun loadPackage(sessionId: String): Result<QuestionSessionPackage> {
        val safeSessionId = sessionId.trim()
        if (safeSessionId.length !in 8..128) {
            return Result.failure(QuestionSessionNotFoundException())
        }
        val studentId = sessionManager.session.first()?.id
            ?: return Result.failure(QuestionSessionExpiredException("انتهت جلسة الدخول."))

        return runCatching {
            var tokens = sessionManager.readTokens() ?: throw QuestionSessionExpiredException("انتهت جلسة الدخول.")
            if (tokens.accessTokenNeedsRefresh(nowEpochSeconds())) {
                tokens = refreshTokens(tokens.refreshToken)
            }
            val data = try {
                requestPackage(tokens, safeSessionId)
            } catch (error: HttpException) {
                if (error.code() != 401) throw error
                tokens = refreshTokens(tokens.refreshToken)
                requestPackage(tokens, safeSessionId)
            }
            val domain = data.toDomain().also {
                validatePackage(it, safeSessionId)
            }
            persistVerifiedPackage(studentId, data, domain)
            loadSnapshot(safeSessionId) ?: domain
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw mapFailure(error)
        }
    }

    override suspend fun loadSnapshot(sessionId: String): QuestionSessionPackage? {
        val safeSessionId = sessionId.trim()
        if (safeSessionId.length !in 8..128) return null
        val studentId = sessionManager.session.first()?.id ?: return null
        return try {
            val entity = localStore.readQuestionSession(studentId, safeSessionId) ?: return null
            val data = gson.fromJson(entity.packageJson, QuestionSessionPackageDataDto::class.java)
                ?: return null
            val mapped = data.toDomain()
            mapped.copy(
                session = mapped.session.copy(status = entity.status),
                progress = QuestionSessionProgress(
                    currentIndex = entity.currentQuestionIndex.coerceIn(0, data.questions.size),
                    totalQuestions = data.questions.size,
                ),
                snapshotSavedAtEpochMillis = entity.updatedAtEpochMillis,
            ).also { validatePackage(it, safeSessionId) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun saveLocalAnswer(
        sessionId: String,
        questionId: String,
        answer: QuestionAnswerInput,
        nextQuestionIndex: Int,
        completed: Boolean,
    ): Result<Unit> {
        val safeSessionId = sessionId.trim()
        val safeQuestionId = questionId.trim()
        if (safeSessionId.length !in 8..128 || safeQuestionId.isBlank()) {
            return Result.failure(QuestionSessionNotFoundException())
        }
        val studentId = sessionManager.session.first()?.id
            ?: return Result.failure(QuestionSessionExpiredException("انتهت جلسة الدخول."))

        return runCatching {
            val session = localStore.readQuestionSession(studentId, safeSessionId)
                ?: throw QuestionSessionNotFoundException("الجلسة غير محفوظة على هذا الجهاز.")
            val packageData = gson.fromJson(
                session.packageJson,
                QuestionSessionPackageDataDto::class.java,
            ) ?: throw QuestionSessionServiceException("تعذر قراءة حزمة الجلسة المحلية.")
            val questionIds = packageData.questions.map { it.id.trim() }
            if (safeQuestionId !in questionIds) {
                throw QuestionSessionNotFoundException("السؤال لا ينتمي إلى الجلسة الحالية.")
            }

            val existingAnswers = localStore.readQuestionAnswers(studentId, safeSessionId)
            val existingAnswer = existingAnswers.firstOrNull { it.questionId == safeQuestionId }
            if (existingAnswer != null && !packageData.policy.allowBack) {
                return@runCatching Unit
            }

            val safeNextIndex = nextQuestionIndex.coerceIn(0, questionIds.size)
            val expectedMinimum = (questionIds.indexOf(safeQuestionId) + 1).coerceAtLeast(1)
            if (safeNextIndex < expectedMinimum) {
                throw QuestionSessionServiceException("موضع السؤال التالي غير صالح.")
            }

            val now = nowEpochMillis()
            val answerJson = answer.toPendingJson()
            val sequence = (existingAnswers.maxOfOrNull { it.localSequence } ?: 0) + 1
            val operationId = stableAnswerOperationId(
                studentId = studentId,
                sessionId = safeSessionId,
                questionId = safeQuestionId,
                localSequence = sequence,
            )
            val operationPayload = JsonObject().apply {
                addProperty("session_id", safeSessionId)
                addProperty("question_id", safeQuestionId)
                add("answer", gson.fromJson(answerJson, JsonObject::class.java))
                addProperty("local_sequence", sequence)
            }

            localStore.recordQuestionAnswer(
                answer = QuestionAnswerEntity(
                    studentId = studentId,
                    sessionId = safeSessionId,
                    questionId = safeQuestionId,
                    answerJson = answerJson,
                    localSequence = sequence,
                    answeredAtEpochMillis = now,
                ),
                nextQuestionIndex = safeNextIndex,
                sessionStatus = if (completed) "completed_local" else "in_progress",
                operation = PendingOperationEntity(
                    operationId = operationId,
                    studentId = studentId,
                    type = "question_answer",
                    payloadJson = gson.toJson(operationPayload),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            runCatching(onPendingAnswerSaved)
            Unit
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw when (error) {
                is QuestionSessionException -> error
                else -> QuestionSessionServiceException("تعذر حفظ الإجابة محليًا.", error)
            }
        }
    }

    override suspend fun syncPendingAnswers(): Result<QuestionAnswerSyncSummary> {
        val studentId = sessionManager.session.first()?.id
            ?: return Result.failure(QuestionSessionExpiredException("انتهت جلسة الدخول."))

        return runCatching {
            localStore.recoverInterruptedOperations(studentId)
            val ready = localStore.readyOperations(
                studentId = studentId,
                limit = 50,
            ).filter { it.type == "question_answer" }

            if (ready.isEmpty()) {
                return@runCatching QuestionAnswerSyncSummary(
                    attempted = 0,
                    confirmed = 0,
                    retryScheduled = 0,
                )
            }

            var tokens = sessionManager.readTokens()
                ?: throw QuestionSessionExpiredException("انتهت جلسة الدخول.")
            if (tokens.accessTokenNeedsRefresh(nowEpochSeconds())) {
                tokens = refreshTokens(tokens.refreshToken)
            }

            var attempted = 0
            var confirmed = 0
            var retryScheduled = 0

            for (operation in ready) {
                attempted += 1
                localStore.markOperationSyncing(operation.operationId)
                try {
                    val request = operation.toAnswerRequest()
                    val result = try {
                        requestAnswer(tokens, operation.operationId, request)
                    } catch (error: HttpException) {
                        if (error.code() != 401) throw error
                        tokens = refreshTokens(tokens.refreshToken)
                        requestAnswer(tokens, operation.operationId, request)
                    }

                    if (result.sessionId != request.sessionId ||
                        result.questionId != request.questionId ||
                        !result.accepted
                    ) {
                        throw QuestionSessionServiceException(
                            "تعذر التحقق من تأكيد الإجابة من الخادم.",
                        )
                    }

                    localStore.markOperationConfirmed(operation.operationId)
                    confirmed += 1
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    val mapped = mapFailure(error)
                    localStore.markOperationFailed(
                        operationId = operation.operationId,
                        error = mapped.message,
                    )
                    retryScheduled += 1
                    // Preserve local answer order. A later run resumes from this exact operation_id.
                    break
                }
            }

            QuestionAnswerSyncSummary(
                attempted = attempted,
                confirmed = confirmed,
                retryScheduled = retryScheduled,
            )
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw mapFailure(error)
        }
    }

    override suspend fun markCompletedLocal(
        sessionId: String,
        allowIncomplete: Boolean,
    ): Result<Unit> {
        val safeSessionId = sessionId.trim()
        if (safeSessionId.length !in 8..128) {
            return Result.failure(QuestionSessionNotFoundException())
        }
        val studentId = sessionManager.session.first()?.id
            ?: return Result.failure(QuestionSessionExpiredException("انتهت جلسة الدخول."))

        return runCatching {
            val session = localStore.readQuestionSession(studentId, safeSessionId)
                ?: throw QuestionSessionNotFoundException("الجلسة غير محفوظة على هذا الجهاز.")
            val answers = localStore.readQuestionAnswers(studentId, safeSessionId)
            val packageData = gson.fromJson(
                session.packageJson,
                QuestionSessionPackageDataDto::class.java,
            ) ?: throw QuestionSessionServiceException("تعذر قراءة حزمة الجلسة المحلية.")
            if (!allowIncomplete &&
                answers.map { it.questionId }.toSet().size < packageData.questions.size
            ) {
                throw QuestionSessionServiceException("أكمل جميع الأسئلة قبل إنهاء الاختبار.")
            }
            val now = nowEpochMillis()
            localStore.saveQuestionSession(
                session.copy(
                    currentQuestionIndex = packageData.questions.size,
                    status = "completed_local",
                    updatedAtEpochMillis = now,
                    completedAtEpochMillis = now,
                ),
            )
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw when (error) {
                is QuestionSessionException -> error
                else -> QuestionSessionServiceException("تعذر تثبيت إكمال الجلسة محليًا.", error)
            }
        }
    }

    override suspend fun loadActiveSeconds(sessionId: String): Int? {
        val safeSessionId = sessionId.trim()
        if (safeSessionId.length !in 8..128) return null
        val studentId = sessionManager.session.first()?.id ?: return null
        return try {
            val document = localStore.readDocument(
                studentId = studentId,
                kind = QUESTION_ACTIVE_TIME_DOCUMENT_KIND,
                documentId = safeSessionId,
            ) ?: return null
            val payload = gson.fromJson(document.payloadJson, JsonObject::class.java)
                ?: return null
            payload.get("seconds")?.takeUnless { it.isJsonNull }?.asInt
                ?.coerceIn(0, 86_400)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun saveActiveSeconds(
        sessionId: String,
        seconds: Int,
    ): Result<Unit> {
        val safeSessionId = sessionId.trim()
        if (safeSessionId.length !in 8..128) {
            return Result.failure(QuestionSessionNotFoundException())
        }
        val studentId = sessionManager.session.first()?.id
            ?: return Result.failure(QuestionSessionExpiredException("انتهت جلسة الدخول."))
        return runCatching {
            val payload = JsonObject().apply {
                addProperty("seconds", seconds.coerceIn(0, 86_400))
            }
            localStore.putDocument(
                studentId = studentId,
                kind = QUESTION_ACTIVE_TIME_DOCUMENT_KIND,
                documentId = safeSessionId,
                payloadJson = gson.toJson(payload),
                savedAtEpochMillis = nowEpochMillis(),
            )
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw QuestionSessionServiceException("تعذر حفظ وقت النشاط محليًا.", error)
        }
    }

    override suspend fun loadResult(sessionId: String): QuestionSessionResult? {
        val safeSessionId = sessionId.trim()
        if (safeSessionId.length !in 8..128) return null
        val studentId = sessionManager.session.first()?.id ?: return null
        return try {
            val document = localStore.readDocument(
                studentId = studentId,
                kind = QUESTION_RESULT_DOCUMENT_KIND,
                documentId = safeSessionId,
            ) ?: return null
            gson.fromJson(document.payloadJson, QuestionSessionResult::class.java)
                ?.also { validateResult(it, safeSessionId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun finishSession(sessionId: String): Result<QuestionSessionResult> {
        val safeSessionId = sessionId.trim()
        if (safeSessionId.length !in 8..128) {
            return Result.failure(QuestionSessionNotFoundException())
        }
        val studentId = sessionManager.session.first()?.id
            ?: return Result.failure(QuestionSessionExpiredException("انتهت جلسة الدخول."))

        loadResult(safeSessionId)?.let { return Result.success(it) }

        return runCatching {
            var tokens = sessionManager.readTokens()
                ?: throw QuestionSessionExpiredException("انتهت جلسة الدخول.")
            if (tokens.accessTokenNeedsRefresh(nowEpochSeconds())) {
                tokens = refreshTokens(tokens.refreshToken)
            }
            val request = QuestionFinishRequestDto(
                sessionId = safeSessionId,
                activeSeconds = loadActiveSeconds(safeSessionId),
            )
            val idempotencyKey = stableFinishOperationId(studentId, safeSessionId)
            val data = try {
                requestFinish(tokens, idempotencyKey, request)
            } catch (error: HttpException) {
                if (error.code() != 401) throw error
                tokens = refreshTokens(tokens.refreshToken)
                requestFinish(tokens, idempotencyKey, request)
            }
            val result = data.toDomain().also {
                validateResult(it, safeSessionId)
            }
            if (data.confirmedDelta.available) {
                localStore.applyConfirmedDelta(
                    data.confirmedDelta.toLocalDelta(expectedStudentId = studentId),
                )
            }
            val now = nowEpochMillis()
            localStore.putDocument(
                studentId = studentId,
                kind = QUESTION_RESULT_DOCUMENT_KIND,
                documentId = safeSessionId,
                payloadJson = gson.toJson(result),
                serverVersion = data.completedAt,
                savedAtEpochMillis = now,
            )
            localStore.readQuestionSession(studentId, safeSessionId)?.let { local ->
                localStore.saveQuestionSession(
                    local.copy(
                        status = "completed_confirmed",
                        updatedAtEpochMillis = now,
                        completedAtEpochMillis = local.completedAtEpochMillis ?: now,
                    ),
                )
            }
            result
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw mapFailure(error)
        }
    }

    private suspend fun requestFinish(
        tokens: AuthTokens,
        idempotencyKey: String,
        request: QuestionFinishRequestDto,
    ): QuestionFinishResultDto {
        val response = questionApi.finishSession(
            authorization = "Bearer ${tokens.accessToken}",
            idempotencyKey = idempotencyKey,
            request = request,
        )
        val data = response.data
        if (!response.success || data == null) {
            val message = response.error?.message.orEmpty()
            when (response.error?.code.orEmpty()) {
                "unauthorized", "invalid_refresh_token", "refresh_expired" ->
                    throw QuestionSessionExpiredException(
                        message.ifBlank { "انتهت جلسة الدخول." },
                    )
                "activity_session_expired", "activity_session_abandoned" ->
                    throw QuestionSessionExpiredException(
                        message.ifBlank { "انتهت صلاحية جلسة النشاط." },
                    )
                "activity_session_not_found", "invalid_session" ->
                    throw QuestionSessionNotFoundException(
                        message.ifBlank { "جلسة النشاط غير متاحة." },
                    )
                else -> throw QuestionSessionServiceException(
                    message.ifBlank { "تعذر إنهاء جلسة الأسئلة الآن." },
                )
            }
        }
        return data
    }

    private fun validateResult(
        result: QuestionSessionResult,
        expectedSessionId: String,
    ) {
        if (result.sessionId != expectedSessionId ||
            result.completedAt.isBlank() ||
            result.score.totalQuestions <= 0 ||
            result.score.correctAnswers < 0 ||
            result.score.partialAnswers < 0 ||
            result.score.incorrectAnswers < 0 ||
            result.score.correctAnswers + result.score.incorrectAnswers != result.score.totalQuestions ||
            result.score.scorePercent !in 0..100 ||
            result.score.passPercent !in 1..100 ||
            result.score.xpEarned < 0.0 ||
            result.score.heartsSpent < 0 ||
            result.score.durationSeconds < 0 ||
            (result.score.attemptId != null && result.score.attemptId <= 0)
        ) {
            throw QuestionSessionServiceException("نتيجة جلسة الأسئلة غير صالحة.")
        }
    }

    private suspend fun requestAnswer(
        tokens: AuthTokens,
        idempotencyKey: String,
        request: QuestionAnswerRequestDto,
    ) = questionApi.submitAnswer(
        authorization = "Bearer ${tokens.accessToken}",
        idempotencyKey = idempotencyKey,
        request = request,
    ).let { response ->
        val data = response.data
        if (!response.success || data == null) {
            val message = response.error?.message.orEmpty()
            when (response.error?.code.orEmpty()) {
                "unauthorized", "invalid_refresh_token", "refresh_expired" ->
                    throw QuestionSessionExpiredException(
                        message.ifBlank { "انتهت جلسة الدخول." },
                    )
                "activity_session_expired", "activity_session_abandoned" ->
                    throw QuestionSessionExpiredException(
                        message.ifBlank { "انتهت صلاحية جلسة النشاط." },
                    )
                "activity_session_not_found", "invalid_session", "question_not_found" ->
                    throw QuestionSessionNotFoundException(
                        message.ifBlank { "الإجابة لا تنتمي إلى جلسة صالحة." },
                    )
                else -> throw QuestionSessionServiceException(
                    message.ifBlank { "تعذر مزامنة الإجابة الآن." },
                )
            }
        }
        data
    }

    private fun PendingOperationEntity.toAnswerRequest(): QuestionAnswerRequestDto {
        val payload = try {
            gson.fromJson(payloadJson, JsonObject::class.java)
        } catch (error: Exception) {
            throw QuestionSessionServiceException("بيانات الإجابة المحلية غير صالحة.", error)
        }
        val sessionId = payload.string("session_id")
        val questionId = payload.string("question_id")
        val answer = payload.getAsJsonObject("answer")
            ?: throw QuestionSessionServiceException("الإجابة المحلية مفقودة.")
        if (sessionId.length !in 8..128 || questionId.isBlank()) {
            throw QuestionSessionServiceException("هوية الإجابة المحلية غير صالحة.")
        }
        return QuestionAnswerRequestDto(
            sessionId = sessionId,
            questionId = questionId,
            answer = answer.deepCopy(),
        )
    }

    private suspend fun requestPackage(
        tokens: AuthTokens,
        expectedSessionId: String,
    ): QuestionSessionPackageDataDto {
        val response = questionApi.sessionPackage(
            authorization = "Bearer ${tokens.accessToken}",
            sessionId = expectedSessionId,
        )
        val data = response.data
        if (!response.success || data == null) {
            val message = response.error?.message.orEmpty()
            when (response.error?.code.orEmpty()) {
                "unauthorized", "invalid_refresh_token", "refresh_expired" ->
                    throw QuestionSessionExpiredException(message.ifBlank { "انتهت جلسة الدخول." })
                "activity_session_expired", "activity_session_abandoned" ->
                    throw QuestionSessionExpiredException(message.ifBlank { "انتهت صلاحية جلسة النشاط." })
                "activity_session_not_found", "invalid_session" ->
                    throw QuestionSessionNotFoundException(message.ifBlank { "جلسة النشاط غير متاحة." })
                "question_source_unavailable" ->
                    throw QuestionSessionSourceUnavailableException(
                        message.ifBlank { "مصدر أسئلة هذا النشاط غير متاح في البيئة الحالية." },
                    )
                else -> throw QuestionSessionServiceException(
                    message.ifBlank { "تعذر تحميل أسئلة الجلسة الآن." },
                )
            }
        }
        return data
    }

    private suspend fun persistVerifiedPackage(
        studentId: String,
        data: QuestionSessionPackageDataDto,
        domain: QuestionSessionPackage,
    ) {
        try {
            val now = nowEpochMillis()
            val existing = localStore.readQuestionSession(studentId, domain.session.id)
            val localStatus = existing?.status?.takeIf {
                it in setOf("ready", "in_progress", "completed_local")
            } ?: "ready"
            localStore.saveQuestionSession(
                QuestionSessionEntity(
                    sessionId = domain.session.id,
                    studentId = studentId,
                    subjectVersionId = domain.session.subjectVersionId,
                    unitId = domain.session.unitId,
                    lessonId = domain.session.lessonId,
                    status = localStatus,
                    packageJson = gson.toJson(data),
                    currentQuestionIndex = maxOf(
                        existing?.currentQuestionIndex ?: 0,
                        domain.progress.currentIndex,
                    ).coerceIn(0, domain.questions.size),
                    serverVersion = domain.version,
                    startedAtEpochMillis = existing?.startedAtEpochMillis ?: now,
                    updatedAtEpochMillis = now,
                    completedAtEpochMillis = existing?.completedAtEpochMillis,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A verified server package remains usable even if local persistence fails.
        }
    }

    private fun validatePackage(
        packageData: QuestionSessionPackage,
        expectedSessionId: String,
    ) {
        if (packageData.session.id != expectedSessionId) {
            throw QuestionSessionNotFoundException("تعذر التحقق من هوية جلسة الأسئلة.")
        }
        if (packageData.version.isBlank() || packageData.session.subjectVersionId <= 0) {
            throw QuestionSessionServiceException("استجابة جلسة الأسئلة غير مكتملة.")
        }
        if (packageData.questions.isEmpty()) {
            throw QuestionSessionSourceUnavailableException("لا توجد أسئلة مدعومة في هذه الجلسة.")
        }
        if (packageData.progress.totalQuestions != packageData.questions.size) {
            throw QuestionSessionServiceException("عدد أسئلة الجلسة غير متطابق.")
        }
        if (packageData.progress.currentIndex !in 0..packageData.questions.size) {
            throw QuestionSessionServiceException("موضع جلسة الأسئلة غير صالح.")
        }

        validateQuestionTypes(
            activityType = packageData.session.activityType,
            questions = packageData.questions,
        )
        if (packageData.questions.map(QuestionItem::id).toSet().size != packageData.questions.size) {
            throw QuestionSessionServiceException("تحتوي الجلسة على معرف سؤال مكرر.")
        }
    }

    private suspend fun refreshTokens(refreshToken: String): AuthTokens {
        try {
            val response = authApi.refresh(StudentRefreshRequestDto(refreshToken))
            val data = response.data
            if (!response.success || data == null) {
                throw QuestionSessionExpiredException(response.error?.message ?: "انتهت جلسة الدخول.")
            }
            return AuthTokens(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                expiresInSeconds = data.expiresIn,
                accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + data.expiresIn,
            ).also { sessionManager.updateTokens(it) }
        } catch (error: HttpException) {
            if (error.code() in 400..403) throw QuestionSessionExpiredException(cause = error)
            throw error
        }
    }

    private fun mapFailure(error: Throwable): Throwable = when (error) {
        is QuestionSessionExpiredException,
        is QuestionSessionNotFoundException,
        is QuestionSessionSourceUnavailableException,
        is QuestionSessionNetworkException,
        is QuestionSessionServiceException,
        -> error
        is IOException -> QuestionSessionNetworkException(error)
        is HttpException -> when (error.code()) {
            401, 403 -> QuestionSessionExpiredException(cause = error)
            404, 422 -> QuestionSessionNotFoundException(cause = error)
            410 -> QuestionSessionExpiredException(cause = error)
            429 -> QuestionSessionServiceException("طلبات كثيرة خلال وقت قصير. حاول بعد قليل.", error)
            503 -> QuestionSessionSourceUnavailableException(cause = error)
            else -> QuestionSessionServiceException(cause = error)
        }
        else -> QuestionSessionServiceException(cause = error)
    }
}

internal fun validateQuestionTypes(
    activityType: String,
    questions: List<QuestionItem>,
) {
    val allowedTypes = when (activityType) {
        "choose_test" -> setOf(QuestionType.Choose)
        "true_false_test" -> setOf(QuestionType.TrueFalse)
        "connect_test" -> setOf(QuestionType.Connect)
        "fill_test" -> setOf(QuestionType.Fill)
        "speed_test" -> setOf(QuestionType.Speed)
        "lesson_practice" -> setOf(
            QuestionType.Choose,
            QuestionType.TrueFalse,
            QuestionType.Connect,
            QuestionType.Fill,
            QuestionType.Direct,
        )
        else -> throw QuestionSessionSourceUnavailableException()
    }
    if (questions.any { it.type !in allowedTypes }) {
        throw QuestionSessionServiceException("نوع سؤال لا يطابق نوع النشاط.")
    }
}

internal fun QuestionConfirmedDeltaDto.toLocalDelta(
    expectedStudentId: String,
): ConfirmedStudentDelta {
    if (!available) {
        throw QuestionSessionServiceException("لا توجد تغييرات طالب مؤكدة لتطبيقها.")
    }
    val owner = studentId?.trim().orEmpty()
    val confirmedAt = confirmedAtEpochMillis ?: 0L
    if (owner != expectedStudentId || confirmedAt <= 0L) {
        throw QuestionSessionServiceException("تعذر التحقق من ملكية تغييرات النتيجة المؤكدة.")
    }
    val mappedSubjects = subjects.map(QuestionConfirmedSubjectDeltaDto::toLocalDelta)
    if (mappedSubjects.map(ConfirmedSubjectDelta::subjectVersionId).toSet().size != mappedSubjects.size) {
        throw QuestionSessionServiceException("تحتوي تغييرات المواد المؤكدة على مادة مكررة.")
    }
    return ConfirmedStudentDelta(
        studentId = owner,
        profile = profile?.toLocalDelta(),
        subjects = mappedSubjects,
        serverVersion = serverVersion?.trim()?.takeIf(String::isNotBlank),
        confirmedAtEpochMillis = confirmedAt,
    )
}

private fun QuestionConfirmedProfileDeltaDto.toLocalDelta(): ConfirmedProfileDelta =
    ConfirmedProfileDelta(
        globalXp = globalXp,
        gems = gems,
        level = level,
        levelProgressPercent = levelProgressPercent,
        levelNextXp = levelNextXp,
        todayXp = todayXp,
        todaySeconds = todaySeconds,
        todayMinutes = todayMinutes,
        todayAttempts = todayAttempts,
        streakCurrentDays = streakCurrentDays,
        unreadNotifications = unreadNotifications,
        smartGuideCompletedSteps = smartGuideCompletedSteps,
        smartGuideTotalSteps = smartGuideTotalSteps,
        smartGuideCompletionPercent = smartGuideCompletionPercent,
    )

private fun QuestionConfirmedSubjectDeltaDto.toLocalDelta(): ConfirmedSubjectDelta {
    if (subjectVersionId <= 0) {
        throw QuestionSessionServiceException("معرف المادة في التغييرات المؤكدة غير صالح.")
    }
    return ConfirmedSubjectDelta(
        subjectVersionId = subjectVersionId,
        points = points,
        level = level,
        levelProgressPercent = levelProgressPercent,
        hearts = hearts,
    )
}

internal fun QuestionFinishResultDto.toDomain(): QuestionSessionResult =
    QuestionSessionResult(
        sessionId = sessionId.trim(),
        completedAt = completedAt.trim(),
        replayed = replayed,
        score = QuestionSessionScore(
            correctAnswers = result.correctAnswers,
            partialAnswers = result.partialAnswers,
            incorrectAnswers = result.incorrectAnswers,
            totalQuestions = result.totalQuestions,
            scorePercent = result.scorePercent,
            passed = result.passed,
            passPercent = result.passPercent,
            xpEarned = result.xpEarned,
            heartsSpent = result.heartsSpent,
            timedOut = result.timedOut,
            durationSeconds = result.durationSeconds,
            attemptId = result.attemptId,
        ),
        policy = policy.toDomain(),
        confirmedDeltaAvailable = confirmedDelta.available,
        confirmedDeltaReason = confirmedDelta.reason.trim(),
    )

internal fun QuestionSessionPackageDataDto.toDomain(): QuestionSessionPackage {
    val domainQuestions = questions.map(QuestionSessionQuestionDto::toDomain)
    return QuestionSessionPackage(
        version = version.trim(),
        generatedAt = generatedAt.trim(),
        session = QuestionSessionInfo(
            id = session.id.trim(),
            status = session.status.trim(),
            expiresAt = session.expiresAt.trim(),
            subjectVersionId = session.subjectVersionId,
            unitId = session.unitId,
            lessonId = session.lessonId,
            activityType = session.activityType.trim(),
            activityMode = session.activityMode.trim(),
            startedAt = session.startedAt.trim(),
            startedAtEpochSeconds = session.startedAtEpochSeconds,
        ),
        progress = QuestionSessionProgress(
            currentIndex = progress.currentIndex,
            totalQuestions = progress.totalQuestions,
        ),
        policy = policy.toDomain(),
        questions = domainQuestions,
    )
}

private fun QuestionSessionPolicyDto.toDomain(): QuestionSessionPolicy =
    QuestionSessionPolicy(
        allowBack = allowBack,
        allowSkip = allowSkip,
        revealAnswers = revealAnswers,
        tfReasonOnlyOnFalse = tfReasonOnlyOnFalse,
        passPercent = passPercent.coerceIn(1, 100),
        timerSeconds = timerSeconds.coerceIn(0, 3600),
        activeTime = activeTime.toDomain(),
        result = result.toDomain(),
    )

private fun QuestionActiveTimePolicyDto.toDomain(): QuestionActiveTimePolicy =
    QuestionActiveTimePolicy(
        enabled = enabled,
        idleSeconds = idleSeconds.coerceIn(5, 900),
        pingInterval = pingInterval.coerceIn(5, 60),
    )

private fun QuestionResultPolicyDto.toDomain(): QuestionResultPolicy =
    QuestionResultPolicy(
        showPassBadge = showPassBadge,
        showScore = showScore,
        showCountsCorrect = showCountsCorrect,
        showCountsPartial = showCountsPartial,
        showCountsWrong = showCountsWrong,
        showXp = showXp,
        showHeartsSpent = showHeartsSpent,
        showRetryButton = showRetryButton,
        showBackButton = showBackButton,
        showReviewDetails = showReviewDetails,
        showMistakesButton = showMistakesButton,
    )

private fun QuestionSessionQuestionDto.toDomain(): QuestionItem {
    val stableType = QuestionType.fromWire(type)
        ?: throw QuestionSessionServiceException("نوع سؤال غير معروف.")
    val safeId = id.trim().ifBlank { throw QuestionSessionServiceException("معرف السؤال مفقود.") }
    val safePrompt = prompt.trim().ifBlank { throw QuestionSessionServiceException("نص السؤال مفقود.") }
    val parsedPayload = when (stableType) {
        QuestionType.Choose,
        QuestionType.Speed,
        -> QuestionPayload.Options(payload.readOptions())

        QuestionType.TrueFalse -> {
            val options = payload.readOptions()
            val requiresReason = payload.boolean("requires_reason")
            val reasons = if (requiresReason) payload.readOptions("reasons") else emptyList()
            if (requiresReason && reasons.isEmpty()) {
                throw QuestionSessionServiceException("أسباب سؤال صح أو خطأ غير مكتملة.")
            }
            QuestionPayload.TrueFalse(
                options = options,
                requiresReason = requiresReason,
                reasonOnlyOnFalse = payload.boolean("reason_only_on_false"),
                reasons = reasons,
            )
        }

        QuestionType.Fill -> {
            val mode = payload.string("input_mode")
            if (mode != "text") throw QuestionSessionServiceException("صيغة سؤال الإكمال غير مدعومة.")
            QuestionPayload.Fill(
                inputMode = mode,
                blanksCount = payload.int("blanks_count").coerceIn(1, 4),
            )
        }

        QuestionType.Direct -> {
            val mode = payload.string("input_mode")
            if (mode != "text") throw QuestionSessionServiceException("صيغة السؤال المباشر غير مدعومة.")
            QuestionPayload.Direct(mode)
        }

        QuestionType.Connect -> {
            val left = payload.readConnectItems("left_items")
            val right = payload.readConnectItems("right_items")
            if (left.size < 2 || left.size != right.size) {
                throw QuestionSessionServiceException("بيانات سؤال التوصيل غير مكتملة.")
            }
            QuestionPayload.Connect(left, right)
        }
    }
    return QuestionItem(
        id = safeId,
        type = stableType,
        prompt = safePrompt,
        payload = parsedPayload,
    )
}

private fun JsonObject.readOptions(key: String = "options"): List<QuestionOption> {
    val array = getAsJsonArray(key) ?: JsonArray()
    val options = array.map { element ->
        val item = element.asJsonObject
        QuestionOption(
            id = item.string("id").ifBlank {
                throw QuestionSessionServiceException("معرف خيار السؤال مفقود.")
            },
            text = item.string("text").ifBlank {
                throw QuestionSessionServiceException("نص خيار السؤال مفقود.")
            },
        )
    }
    if (options.size < 2 || options.map(QuestionOption::id).toSet().size != options.size) {
        throw QuestionSessionServiceException("خيارات السؤال غير صالحة.")
    }
    return options
}

private fun JsonObject.readConnectItems(key: String): List<ConnectItem> {
    val array = getAsJsonArray(key) ?: JsonArray()
    val items = array.map { element ->
        val item = element.asJsonObject
        ConnectItem(
            id = item.string("id").ifBlank {
                throw QuestionSessionServiceException("معرف عنصر التوصيل مفقود.")
            },
            text = item.string("text").ifBlank {
                throw QuestionSessionServiceException("نص عنصر التوصيل مفقود.")
            },
        )
    }
    if (items.map(ConnectItem::id).toSet().size != items.size) {
        throw QuestionSessionServiceException("معرفات عناصر التوصيل مكررة.")
    }
    return items
}

private fun QuestionAnswerInput.toPendingJson(): String {
    val payload = JsonObject()
    when (this) {
        is QuestionAnswerInput.Choice -> {
            val safeOption = optionId.trim()
            if (safeOption.isBlank()) throw QuestionSessionServiceException("لم يتم اختيار إجابة.")
            payload.addProperty("kind", "choice")
            payload.addProperty("option_id", safeOption)
            reasonId?.trim()?.takeIf(String::isNotBlank)?.let {
                payload.addProperty("reason_id", it)
            }
        }

        is QuestionAnswerInput.Text -> {
            val safeText = value.trim()
            if (safeText.isBlank()) throw QuestionSessionServiceException("أدخل الإجابة أولًا.")
            payload.addProperty("kind", "text")
            payload.addProperty("text", safeText)
        }

        is QuestionAnswerInput.Fill -> {
            if (values.isEmpty() || values.size > 4) {
                throw QuestionSessionServiceException("إجابات الفراغات غير مكتملة.")
            }
            val array = JsonArray()
            values.forEach { value ->
                array.add(value.trim())
            }
            payload.addProperty("kind", "fill")
            payload.add("blanks", array)
        }

        is QuestionAnswerInput.Connections -> {
            if (pairs.isEmpty()) throw QuestionSessionServiceException("أكمل التوصيل أولًا.")
            val array = JsonArray()
            pairs.forEach { pair: ConnectAnswerPair ->
                val left = pair.leftId.trim()
                val right = pair.rightId.trim()
                if (left.isBlank() || right.isBlank()) {
                    throw QuestionSessionServiceException("بيانات التوصيل غير مكتملة.")
                }
                array.add(JsonObject().apply {
                    addProperty("left_id", left)
                    addProperty("right_id", right)
                })
            }
            if (pairs.map { it.leftId }.toSet().size != pairs.size ||
                pairs.map { it.rightId }.toSet().size != pairs.size
            ) {
                throw QuestionSessionServiceException("لا يمكن استخدام عنصر التوصيل أكثر من مرة.")
            }
            payload.addProperty("kind", "connections")
            payload.add("pairs", array)
        }

        QuestionAnswerInput.Skip -> {
            payload.addProperty("kind", "skip")
        }
    }
    return Gson().toJson(payload)
}

private fun stableAnswerOperationId(
    studentId: String,
    sessionId: String,
    questionId: String,
    localSequence: Int,
): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest("$studentId|$sessionId|$questionId|$localSequence".toByteArray(Charsets.UTF_8))
    val hex = bytes.joinToString("") { "%02x".format(it) }
    return "question-answer:$hex"
}

private fun stableFinishOperationId(
    studentId: String,
    sessionId: String,
): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest("$studentId|$sessionId".toByteArray(Charsets.UTF_8))
    val hex = bytes.joinToString("") { "%02x".format(it) }
    return "question-finish:$hex"
}

private fun JsonObject.string(key: String): String =
    get(key)?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()

private fun JsonObject.boolean(key: String): Boolean =
    get(key)?.takeUnless { it.isJsonNull }?.asBoolean ?: false

private fun JsonObject.int(key: String): Int =
    get(key)?.takeUnless { it.isJsonNull }?.asInt ?: 1
