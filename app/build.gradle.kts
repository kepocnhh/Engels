import com.android.build.api.variant.ComponentIdentity
import sp.gx.core.buildDir
import sp.gx.core.camelCase
import sp.gx.core.existing
import sp.gx.core.file
import sp.gx.core.filled
import sp.gx.core.kebabCase

repositories {
    google()
    mavenCentral()
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
}

plugins {
    id("com.android.application")
    id("kotlin-android")
}

fun ComponentIdentity.getVersion(): String {
    val flavors = productFlavors.map { (it, _) -> it }
    check(flavors.isEmpty()) { "Flavors \"$flavorName\" are not supported!" }
    val versionName = android.defaultConfig.versionName ?: error("No version name!")
    check(versionName.isNotBlank())
    val versionCode = android.defaultConfig.versionCode ?: error("No version code!")
    check(versionCode > 0)
    check(name.isNotBlank())
    return when (buildType) {
        "debug" -> kebabCase(
            versionName,
            name,
            versionCode.toString(),
        )
        "release" -> kebabCase(
            versionName,
            versionCode.toString(),
        )
        else -> error("Build type \"${buildType}\" is not supported!")
    }
}

android {
    namespace = "org.kepocnhh.engels"
    compileSdk = Version.Android.compileSdk

    defaultConfig {
        applicationId = namespace
        minSdk = Version.Android.minSdk
        targetSdk = Version.Android.targetSdk
        versionCode = 1
        versionName = "0.0.$versionCode"
        manifestPlaceholders["appName"] = "@string/app_name"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".$name"
            versionNameSuffix = "-$name"
            isMinifyEnabled = false
            isShrinkResources = false
            manifestPlaceholders["buildType"] = name
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions.kotlinCompilerExtensionVersion = "1.5.10" // kotlin 1.9.22
}

androidComponents.onVariants { variant ->
    val output = variant.outputs.single()
    check(output is com.android.build.api.variant.impl.VariantOutputImpl)
    output.outputFileName = "${kebabCase(rootProject.name, variant.getVersion())}.apk"
    afterEvaluate {
        tasks.getByName<JavaCompile>(camelCase("compile", variant.name, "JavaWithJavac")) {
            targetCompatibility = Version.jvmTarget
        }
        tasks.getByName<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>(camelCase("compile", variant.name, "Kotlin")) {
            kotlinOptions.jvmTarget = Version.jvmTarget
        }
        val checkManifestTask = task(camelCase("checkManifest", variant.name)) {
            dependsOn(camelCase("process", variant.name, "Manifest"))
            doLast {
                val file = buildDir()
                    .dir("intermediates/merged_manifests")
                    .dir(variant.name)
                    .dir(camelCase("process", variant.name, "Manifest"))
                    .file("AndroidManifest.xml")
                    .existing()
                    .file()
                    .filled()
                val manifest = groovy.xml.XmlParser().parse(file)
                val actual = manifest.getAt(groovy.namespace.QName("uses-permission")).map {
                    check(it is groovy.util.Node)
                    val attributes = it.attributes().mapKeys { (k, _) -> k.toString() }
                    val name = attributes["{http://schemas.android.com/apk/res/android}name"]
                    check(name is String && name.isNotEmpty())
                    name
                }
                val applicationId by variant.applicationId
                val expected = setOf(
                    "android.permission.INTERNET",
                    "android.permission.POST_NOTIFICATIONS",
                    "$applicationId.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                )
                check(actual.sorted() == expected.sorted()) {
                    "Actual is:\n$actual\nbut expected is:\n$expected"
                }
            }
        }
        tasks.getByName(camelCase("assemble", variant.name)) {
            dependsOn(checkManifestTask)
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.compose.foundation:foundation:${Version.Android.compose}")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
