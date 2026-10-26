package com.example.catalog.tests.extra

/** Своя метка для отбора тестов: `-e annotation com.example.catalog.tests.extra.Smoke`. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class Smoke
