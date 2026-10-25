package cn.luorenmu.plugins.equipment

import cn.luorenmu.request.api.entity.response.dakgg.DakGGItemsResponse.Item

data class ItemRecipeNode(val item: Item, val children: List<ItemRecipeNode> = emptyList())

internal fun itemMaterialIds(item: Item): List<Long> {
    // makeMaterials contains the flattened full recipe; only makeMaterial1/2 are direct children.
    val direct = listOfNotNull(item.makeMaterial1, item.makeMaterial2).filter { it > 0 }
    return direct.ifEmpty { item.makeMaterials.orEmpty().filter { it > 0 } }
}

internal fun buildItemRecipeTree(item: Item, items: List<Item>): ItemRecipeNode {
    val byId = items.associateBy { it.id }
    fun build(current: Item, ancestors: Set<Long>, depth: Int): ItemRecipeNode {
        if (current.id in ancestors || depth >= 8) return ItemRecipeNode(current)
        val nextAncestors = ancestors + current.id
        return ItemRecipeNode(current, itemMaterialIds(current).map { id ->
            build(byId[id] ?: Item(id = id, name = "材料 $id"), nextAncestors, depth + 1)
        })
    }
    return build(item, emptySet(), 0)
}

internal data class RecipePlacement(val node: ItemRecipeNode, val depth: Int, val centerY: Float, val index: Int, val parentIndex: Int?)

internal fun layoutItemRecipeTree(root: ItemRecipeNode): List<RecipePlacement> {
    val result = mutableListOf<RecipePlacement>()
    var leaf = 0
    fun visit(node: ItemRecipeNode, depth: Int, parentIndex: Int?): Float {
        val index = result.size
        result += RecipePlacement(node, depth, 0f, index, parentIndex)
        val centerY = if (node.children.isEmpty()) {
            48f + leaf++ * 96f
        } else {
            val centers = node.children.map { visit(it, depth + 1, index) }
            (centers.first() + centers.last()) / 2f
        }
        result[index] = result[index].copy(centerY = centerY)
        return centerY
    }
    visit(root, 0, null)
    return result
}
