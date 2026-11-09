package com.maruhxn.fflux

import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.CompositeArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import jakarta.persistence.Entity
import kotlin.test.assertTrue

/**
 * `docs/decisions.md` 9절의 ArchUnit 규칙.
 * 모듈 간 참조 금지(6번)는 Gradle 의존으로도 막히지만, 규칙을 한곳에서 읽을 수 있게 여기서도 검사한다.
 * 뼈대 단계라 아직 클래스가 없는 패키지가 있으므로 빈 대상은 통과로 둔다.
 */
@AnalyzeClasses(packages = [ROOT], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {

    @ArchTest
    val `1 domain은 port와 application을 참조하지 않는다`: ArchRule =
        noClasses().that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(PORT, APPLICATION)
            .allowEmptyShould(true)

    @ArchTest
    val `2 domain은 허용 목록만 참조한다`: ArchRule =
        classes().that().resideInAPackage(DOMAIN)
            // KSP가 생성한 QueryDSL Q 클래스(엔티티는 EntityPath, 값 객체는 BeanPath)는 com.querydsl을 참조하므로 제외한다.
            .and().areNotAssignableTo("com.querydsl.core.types.Path")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "java..",
                "kotlin..",
                "org.jetbrains.annotations..",
                "jakarta.persistence..",
                "org.hibernate..",
                "org.springframework..",
                "$ROOT.common..",
                DOMAIN,
            )
            .allowEmptyShould(true)

    @ArchTest
    val `3 application은 port를 참조하지 않는다`: ArchRule =
        noClasses().that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAPackage(PORT)
            .allowEmptyShould(true)

    @ArchTest
    val `4 port web은 application required를 참조하지 않는다`: ArchRule =
        noClasses().that().resideInAPackage("$ROOT.*.port.web..")
            .should().dependOnClassesThat().resideInAPackage("$ROOT.*.application.required..")
            .allowEmptyShould(true)

    @ArchTest
    val `5 port persistence 외의 port는 Entity를 참조하지 않는다`: ArchRule =
        noClasses().that().resideInAPackage(PORT)
            .and().resideOutsideOfPackage("$ROOT.*.port.persistence..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity::class.java)
            .allowEmptyShould(true)

    @ArchTest
    val `6 모듈끼리 서로 참조하지 않는다`: ArchRule =
        MODULES.map { module ->
            noClasses().that().resideInAPackage("$ROOT.$module..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(*(MODULES - module).map { "$ROOT.$it.." }.toTypedArray())
                .allowEmptyShould(true)
        }.let { rules -> rules.drop(1).fold(CompositeArchRule.of(rules.first())) { acc, rule -> acc.and(rule) } }

    @ArchTest
    val `7 app 어댑터는 모듈의 application provided와 required만 참조한다`: ArchRule =
        noClasses().that().resideInAPackage("$ROOT.adapter..")
            .should().dependOnClassesThat(
                resideInAnyPackage(*MODULES.map { "$ROOT.$it.." }.toTypedArray())
                    .and(not(resideInAnyPackage("$ROOT.*.application.provided..", "$ROOT.*.application.required.."))),
            )
            .allowEmptyShould(true)

    @ArchTest
    fun `모듈 패키지는 모두 MODULES에 등록돼 있다`(classes: JavaClasses) {
        // 6·7번 규칙은 MODULES에 있는 모듈만 검사하므로, 등록을 빠뜨리면 그 모듈이 조용히 검사에서 빠진다.
        val unregistered = classes
            .map { it.packageName }
            .filter { it.startsWith("$ROOT.") }
            .map { it.removePrefix("$ROOT.").substringBefore('.') }
            .toSet() - APP_PACKAGES - "common" - MODULES.toSet()

        assertTrue(unregistered.isEmpty(), "ArchitectureTest.MODULES에 등록되지 않은 모듈 패키지: $unregistered")
    }

    companion object {
        /** 새 모듈을 추가하면 여기에도 추가한다. */
        val MODULES = listOf("member", "broadcast", "realtime", "recording")

        /** `app` 모듈의 최상위 패키지. 모듈이 아니다. */
        val APP_PACKAGES = setOf("adapter", "auth", "config", "web")
    }
}

private const val ROOT = "com.maruhxn.fflux"
private const val DOMAIN = "$ROOT.*.domain.."
private const val PORT = "$ROOT.*.port.."
private const val APPLICATION = "$ROOT.*.application.."
