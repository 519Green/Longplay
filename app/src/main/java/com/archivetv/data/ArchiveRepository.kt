package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.archivetv.domain.ArchiveItem
import com.archivetv.domain.PlayableFile

data class ItemDetail(val item: ArchiveItem, val playable: PlayableFile?)

class ArchiveRepository(
    private val service: InternetArchiveService,
    private val fileSelector: PlayableFileSelector,
    private val queryBuilder: SearchQueryBuilder,
) {
    suspend fun browseCollection(collectionId: String, limit: Int): List<ArchiveItem> =
        service.search(
            query = queryBuilder.collection(collectionId),
            rows = limit,
            sort = "downloads desc",
        ).response.docs.map {
            ArchiveItem(it.identifier, it.title ?: it.identifier, it.year)
        }

    suspend fun search(text: String, limit: Int): List<ArchiveItem> =
        service.search(
            query = queryBuilder.userSearch(text),
            rows = limit,
            sort = "downloads desc",
        ).response.docs.map {
            ArchiveItem(it.identifier, it.title ?: it.identifier, it.year)
        }

    suspend fun detail(identifier: String): ItemDetail {
        val dto = service.metadata(identifier)
        val item = ArchiveItem(
            identifier = identifier,
            title = dto.metadata?.title ?: identifier,
            year = dto.metadata?.year,
            description = dto.metadata?.description,
        )
        return ItemDetail(item, fileSelector.select(identifier, dto.files))
    }
}
