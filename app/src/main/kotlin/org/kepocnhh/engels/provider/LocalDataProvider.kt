package org.kepocnhh.engels.provider

import org.kepocnhh.engels.entity.ItemsUploadRequest
import org.kepocnhh.engels.entity.Meta
import java.util.UUID

internal interface LocalDataProvider {
    var metas: List<Meta>
    var items: Map<UUID, ByteArray>
    var requests: List<ItemsUploadRequest>
}
