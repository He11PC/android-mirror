plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}

tasks.register<Delete>("clean") {
    group = "build"
    description = "Delete the build directory to obtain a clean project"
    delete(getLayout().buildDirectory)
}