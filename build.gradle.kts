plugins {
	kotlin("jvm") version "2.1.0"
	kotlin("kapt") version "2.1.0"
	kotlin("plugin.spring") version "2.1.0"
	kotlin("plugin.jpa") version "2.1.0"
	id("org.springframework.boot") version "3.4.1"
	id("io.spring.dependency-management") version "1.1.7"
    id ("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

allOpen {
    // 아래 어노테이션들이 붙은 클래스를 모두 'open' 으로 설정
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

fun getGitHash(): String {
	return runCatching {
		providers.exec {
			commandLine("git", "rev-parse", "--short", "HEAD")
		}.standardOutput.asText.get().trim()
	}.getOrDefault("local")
}

group = "kr.hhplus.be"
version = getGitHash()

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
		jvmToolchain(17)
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
	}
}

dependencies {
	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Spring
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // QueryDSL
    implementation("io.github.openfeign.querydsl:querydsl-jpa:7.0")
    ksp("io.github.openfeign.querydsl:querydsl-ksp-codegen:7.0")

    // DB
	runtimeOnly("com.mysql:mysql-connector-j")
    // 분산락
    implementation("org.redisson:redisson-spring-boot-starter:3.52.0")
    // Redis 기본 라이브러리
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // JWT & Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")

    // Tsid
    implementation("io.hypersistence:hypersistence-tsid:2.1.1")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:mysql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.14.6")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}

tasks.withType<Test> {
	useJUnitPlatform()
	systemProperty("user.timezone", "UTC")
}

// 기본 test 태스크에서는 대량 데이터 시딩 + EXPLAIN 측정을 하는 perf 테스트를 제외한다.
// (Step4 선택 심화 ① 인덱스 보고서용 측정 하네스 — docs/report/step4-index-report.md 참고)
tasks.test {
	useJUnitPlatform {
		excludeTags("perf")
	}
}

tasks.register<Test>("perfTest") {
	group = "verification"
	description = "인덱스 성능 측정 테스트 (대량 데이터 시딩 + EXPLAIN/EXPLAIN ANALYZE, docs/report/data/에 결과 저장)"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("perf")
	}
	systemProperty("user.timezone", "UTC")
	// 측정 결과가 매번 바뀔 수 있으므로 캐시하지 않고 항상 재실행한다.
	outputs.upToDateWhen { false }
}
