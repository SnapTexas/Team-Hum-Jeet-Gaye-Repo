// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    jacoco
}

// Configure all projects
allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview"
            )
        }
    }
}

// Aggregate coverage report task
tasks.register<JacocoReport>("jacocoRootReport") {
    group = "verification"
    description = "Generate aggregate Jacoco coverage report"
    
    dependsOn(
        ":app:jacocoTestReport",
        ":domain:jacocoTestReport",
        ":data:jacocoTestReport",
        ":ml:jacocoTestReport"
    )
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    val reportDir = file("${buildDir}/reports/jacoco/jacocoRootReport")
    reports.html.outputLocation.set(reportDir)
    reports.xml.outputLocation.set(file("${reportDir}/jacocoRootReport.xml"))
    
    doLast {
        println("Aggregate coverage report generated at: ${reportDir}/html/index.html")
    }
}
