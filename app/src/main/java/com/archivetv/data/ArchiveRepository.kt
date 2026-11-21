package com.archivetv.data

import com.archivetv.data.api.InternetArchiveService
import com.archivetv.data.api.SearchDocDto
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
        ).response.docs.map { it.toItem() }

    suspend fun search(text: String, limit: Int): List<ArchiveItem> {
        val query = queryBuilder.userSearch(text)
        if (query.isEmpty()) return emptyList()
        return service.search(
            query = query,
            rows = limit,
            sort = "downloads desc",
        ).response.docs.map { it.toItem() }
    }

    suspend fun detail(identifier: String): ItemDetail {
        val dto = service.metadata(identifier)
        val meta = dto.metadata
        val description = meta?.description.orEmpty()
            .map(HtmlText::toPlain)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val item = ArchiveItem(
            identifier = identifier,
            title = cleanTitle(meta?.title.orEmpty(), identifier),
            year = meta?.year?.firstOrNull { it.isNotBlank() },
            description = description.ifBlank { null },
        )
        return ItemDetail(item, fileSelector.select(identifier, dto.files))
    }

    private fun SearchDocDto.toItem() = ArchiveItem(
        identifier = identifier,
        title = cleanTitle(title, identifier),
        year = year.firstOrNull { it.isNotBlank() },
    )

    private fun cleanTitle(titles: List<String>, identifier: String): String =
        titles.map(HtmlText::toPlain).firstOrNull { it.isNotBlank() } ?: identifier
}
