package com.maruhxn.fflux.support

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/** HTTP로 호출한 요청은 테스트 트랜잭션 롤백이 적용되지 않으므로 테스트마다 테이블을 비운다. */
@Component
class DatabaseCleaner(private val jdbcTemplate: JdbcTemplate) {

    fun clean() {
        val tables = jdbcTemplate.queryForList(
            """
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
            """.trimIndent(),
            String::class.java,
        )
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0")
        tables.forEach { jdbcTemplate.execute("TRUNCATE TABLE `$it`") }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1")
    }
}
