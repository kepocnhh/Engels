package org.kepocnhh.engels.entity

import java.util.UUID
import kotlin.time.Duration

internal data class Meta(
    val id: UUID,
    val created: Duration,
    val updated: Duration,
    val hash: String,
)
