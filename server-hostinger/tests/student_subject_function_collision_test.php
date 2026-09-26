<?php
declare(strict_types=1);

function phase12_function_names(string $path): array
{
    $source = file_get_contents($path);
    if ($source === false) {
        fwrite(STDERR, "Unable to read {$path}\n");
        exit(1);
    }

    $tokens = token_get_all($source);
    $names = [];
    $count = count($tokens);

    for ($i = 0; $i < $count; $i++) {
        $token = $tokens[$i];
        if (!is_array($token) || $token[0] !== T_FUNCTION) {
            continue;
        }

        for ($j = $i + 1; $j < $count; $j++) {
            $next = $tokens[$j];
            if (is_array($next) && $next[0] === T_STRING) {
                $names[] = $next[1];
                break;
            }
            if ($next === '(') {
                break; // anonymous function
            }
        }
    }

    return array_values(array_unique($names));
}

$apiDir = dirname(__DIR__) . '/public_html/api';
$subject = phase12_function_names($apiDir . '/_student_subject.php');
$progress = phase12_function_names($apiDir . '/_student_subject_progress.php');
$duplicates = array_values(array_intersect($subject, $progress));

if ($duplicates !== []) {
    fwrite(
        STDERR,
        "Duplicate subject helper functions detected: " . implode(', ', $duplicates) . PHP_EOL,
    );
    exit(1);
}

echo "student_subject_function_collision_test: ok" . PHP_EOL;
