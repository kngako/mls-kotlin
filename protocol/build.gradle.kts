plugins {
  kotlin("jvm")

  `java-library`
  `java-test-fixtures`
}

val projectVersion: String by project

group = "com.github.traderjoe95.mls"
version = projectVersion

repositories {
  mavenCentral()
}

// Language Stack Dependency Versions
val coroutinesVersion: String by project

// Dependency Versions
val arrowVersion: String by project

val bouncycastleVersion: String by project

// Test Dependency Versions
val kotestVersion: String by project
val kotestArrowVersion: String by project
val mockkVersion: String by project

val vertxVersion: String by project

dependencies {
  // Kotlin Standard Library
  implementation(kotlin("stdlib-jdk8")) // KMP-ready
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion") // KMP-ready

  // Arrow
  api(platform("io.arrow-kt:arrow-stack:$arrowVersion")) // KMP-ready
  api("io.arrow-kt:arrow-core") // KMP-ready

  // Codec
  api(project(":codec"))

  // Crypto
  implementation("org.bouncycastle:bcprov-jdk18on:$bouncycastleVersion") // TODO: Replace with KMP Library
  implementation("org.bouncycastle:bcpkix-jdk18on:$bouncycastleVersion") // TODO: Replace with KMP Library

  // KMP
  implementation("dev.whyoleg.cryptography:cryptography-core:0.3.1") // Replaces org.bouncycastle:bcprov-jdk18on

  // Test Dependencies
  // Tests don't really need to be KMP ready...
  testImplementation(kotlin("test"))

  testImplementation(platform("io.kotest:kotest-bom:$kotestVersion"))
  testImplementation("io.kotest:kotest-runner-junit5")
  testImplementation("io.kotest:kotest-assertions-core")
  testImplementation("io.kotest:kotest-property")
  testImplementation("io.kotest.extensions:kotest-assertions-arrow:$kotestArrowVersion")

  testImplementation("io.mockk:mockk:$mockkVersion")

  testImplementation(testFixtures(project(":codec")))
  testImplementation(project(":interop"))

  testImplementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  testImplementation("io.vertx:vertx-lang-kotlin")
  testImplementation("io.vertx:vertx-lang-kotlin-coroutines")

  testFixturesApi(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  testFixturesApi("io.vertx:vertx-core")
  testFixturesImplementation("io.vertx:vertx-lang-kotlin")
  testFixturesImplementation("io.vertx:vertx-lang-kotlin-coroutines")
}

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.koverHtmlReport, tasks.koverXmlReport, tasks.koverBinaryReport)
}

kotlin {
  jvmToolchain(21)

  compilerOptions {
    allWarningsAsErrors.set(true)
    freeCompilerArgs.addAll("-Xcontext-receivers", "-X")
  }
}

ktlint {
  version.set("1.1.1")
}
