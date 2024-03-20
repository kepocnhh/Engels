package org.kepocnhh.engels.entity

import java.util.UUID
import kotlin.time.Duration

internal data class Session(
    val id: UUID,
    val expires: Duration,
)
