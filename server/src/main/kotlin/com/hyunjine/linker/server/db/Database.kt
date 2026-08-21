package com.hyunjine.linker.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database as ExposedDatabase
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource

/**
 * 서버 전역 DB 초기화. 애플리케이션 시작 시 [init] 을 한 번 호출한다.
 *  1) HikariCP 풀 생성
 *  2) Flyway 로 `db/migration` 마이그레이션 적용
 *  3) Exposed 에 커넥션 등록
 *
 * 마이그레이션 실패는 곧 헬스체크 실패로 이어져 배포 롤백 유도.
 */
object Database {
    private lateinit var dataSource: HikariDataSource

    fun init(config: ApplicationConfig) {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.property("db.url").getString()
            username = config.property("db.user").getString()
            password = config.property("db.password").getString()
            maximumPoolSize = config.propertyOrNull("db.maxPoolSize")?.getString()?.toIntOrNull() ?: 5
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            driverClassName = "org.postgresql.Driver"
        }
        dataSource = HikariDataSource(hikariConfig)

        migrate(dataSource)
        ExposedDatabase.connect(dataSource)
    }

    /** Cloud Run readiness 용. 실제 쿼리 한 방으로 DB 살아있는지 확인. */
    fun isHealthy(): Boolean = try {
        transaction { exec("SELECT 1") { it.next() } }
        true
    } catch (t: Throwable) {
        false
    }

    private fun migrate(ds: DataSource) {
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
