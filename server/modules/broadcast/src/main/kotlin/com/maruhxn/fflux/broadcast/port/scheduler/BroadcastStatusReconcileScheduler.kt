package com.maruhxn.fflux.broadcast.port.scheduler

import com.maruhxn.fflux.broadcast.application.provided.BroadcastStatusReconciler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** 앱이 뜨자마자 한 번, 그 뒤 30초마다 상태를 맞춘다. 통합 테스트는 `fflux.mediamtx.reconcile.enabled=false`로 끄고 직접 부른다. */
@Component
@ConditionalOnProperty("fflux.mediamtx.reconcile.enabled", matchIfMissing = true)
class BroadcastStatusReconcileScheduler(
    private val broadcastStatusReconciler: BroadcastStatusReconciler,
) {
    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    fun reconcile() = broadcastStatusReconciler.reconcile()
}
