-- مرجع مخطط فقط للمرحلة الخامسة. لا يحتوي بيانات ولا يُشغّل على الإنتاج.

CREATE TABLE `app_users` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `role` varchar(32) NOT NULL,
  `full_name` longtext DEFAULT NULL,
  `username` varchar(191) NOT NULL,
  `phone` longtext DEFAULT NULL,
  `email` longtext DEFAULT NULL,
  `city_id` bigint(20) DEFAULT NULL,
  `school_id` bigint(20) DEFAULT NULL,
  `grade_id` bigint(20) DEFAULT NULL,
  `gender` longtext DEFAULT NULL,
  `student_personality` longtext DEFAULT NULL,
  `password_hash` longtext DEFAULT NULL,
  `is_active` bigint(20) DEFAULT NULL,
  `created_at` longtext DEFAULT NULL,
  `student_code` varchar(64) DEFAULT NULL,
  `manual_created` tinyint(4) NOT NULL DEFAULT 0,
  `manual_status` varchar(20) NOT NULL DEFAULT 'active',
  `archived_at` datetime DEFAULT NULL,
  `suspended_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_app_users_student_code` (`student_code`),
  KEY `idx_app_users_role` (`role`),
  KEY `idx_app_users_grade` (`grade_id`),
  KEY `idx_app_users_school` (`school_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ملاحظة مهمة: النسخة المرفقة لا تحتوي UNIQUE على username.

CREATE TABLE `cities` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` longtext DEFAULT NULL,
  `is_active` bigint(20) DEFAULT NULL,
  `requires_school` tinyint(1) NOT NULL DEFAULT 1,
  `curriculum_version_id` bigint(20) UNSIGNED DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cities_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `schools` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `city_id` bigint(20) DEFAULT NULL,
  `name` longtext DEFAULT NULL,
  `is_active` bigint(20) DEFAULT NULL,
  `student_visibility` varchar(20) NOT NULL DEFAULT 'public',
  `directorate` varchar(191) NOT NULL DEFAULT '',
  `directorate_key` varchar(191) NOT NULL DEFAULT '',
  `name_key` varchar(191) NOT NULL DEFAULT '',
  `curriculum_version_id` bigint(20) UNSIGNED DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_schools_city_dir_name_key` (`city_id`,`directorate_key`,`name_key`),
  KEY `idx_schools_city_active` (`city_id`,`is_active`),
  KEY `idx_schools_student_visibility` (`student_visibility`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `grades` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` longtext DEFAULT NULL,
  `sort_order` bigint(20) DEFAULT NULL,
  `is_active` bigint(20) DEFAULT NULL,
  `student_visibility` varchar(20) NOT NULL DEFAULT 'public',
  PRIMARY KEY (`id`),
  KEY `idx_grades_student_visibility` (`student_visibility`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `curriculum_versions` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` varchar(191) NOT NULL,
  `description` text DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_curriculum_versions_name` (`name`),
  KEY `idx_curriculum_versions_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
