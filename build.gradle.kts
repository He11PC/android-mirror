plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
}

tasks.register<Delete>("clean") {
    delete(getLayout().buildDirectory)
}