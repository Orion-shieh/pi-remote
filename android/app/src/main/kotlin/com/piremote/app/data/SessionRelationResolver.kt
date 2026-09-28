package com.piremote.app.data

/**
 * Kind of relationship for a session within a session tree.
 */
enum class SessionRelationKind {
    /** Root session that has spawned child branches. */
    ROOT,
    /** Branch session that derived from a parent session. */
    BRANCH,
    /** Standalone session with neither parent nor branches. */
    STANDALONE,
}

/**
 * Detailed relationship metadata for a session.
 */
data class SessionRelationInfo(
    val kind: SessionRelationKind,
    val parentSessionId: String? = null,
    val parentFile: String? = null,
    val parentDisplayName: String? = null,
    /** Number of direct child branches spawned from this session. */
    val childCount: Int = 0,
    val childDisplayNames: List<String> = emptyList(),
    /** Number of other sibling branches sharing the same parent. */
    val siblingCount: Int = 0,
    val siblingDisplayNames: List<String> = emptyList(),
    /** Depth in the branch hierarchy (0 for root/standalone, 1 for direct branch, 2 for nested branch). */
    val treeDepth: Int = 0,
)

/**
 * A session entry positioned in a visual tree hierarchy.
 */
data class SessionTreeItem(
    val session: PiSessionBrief,
    val depth: Int,
)

/**
 * A root session and all its derivative branches grouped together into a single box/card.
 */
data class SessionTreeGroup(
    val root: PiSessionBrief,
    val items: List<SessionTreeItem>,
)

/**
 * Computes lineage and branch relationships across sessions:
 * - Detects whether a session is a branch of another session (and which one).
 * - Identifies sibling branches (并列分支) that originate from the same parent.
 * - Identifies root sessions (主干会话) that have spawned child branches.
 * - Builds hierarchical session tree groups with indents for nested branches.
 */
object SessionRelationResolver {

    /**
     * Builds hierarchical session tree groups where each root session and all its
     * branches (and sub-branches) are grouped together in one box/card.
     */
    fun buildTreeGroups(
        sessions: List<PiSessionBrief>,
        extraLineage: Map<String, String> = emptyMap(),
    ): List<SessionTreeGroup> {
        if (sessions.isEmpty()) return emptyList()

        val idMap = sessions.associateBy { it.id }
        val fileMap = sessions.associateBy { normalizeFilePath(it.file) }
        val fileBaseMap = sessions.associateBy { it.file.substringAfterLast('/').substringAfterLast('\\') }

        val parentMap = resolveParentMap(sessions, extraLineage, idMap, fileMap, fileBaseMap)

        // parentId -> list of child PiSessionBrief
        val childrenMap = mutableMapOf<String, MutableList<PiSessionBrief>>()
        for ((childId, parentId) in parentMap) {
            val childSession = idMap[childId] ?: continue
            childrenMap.getOrPut(parentId) { mutableListOf() }.add(childSession)
        }

        // Sort children chronologically so earlier branches appear before later branches
        for ((_, cList) in childrenMap) {
            cList.sortBy { it.timestamp.orEmpty() }
        }

        // Roots are sessions that have no parent in the current list
        val rootSessions = sessions.filter { !parentMap.containsKey(it.id) }
            .sortedByDescending { it.timestamp.orEmpty() }

        val visited = mutableSetOf<String>()
        val groups = mutableListOf<SessionTreeGroup>()

        fun collectDescendants(session: PiSessionBrief, depth: Int, acc: MutableList<SessionTreeItem>) {
            if (!visited.add(session.id)) return
            acc.add(SessionTreeItem(session, depth))
            val children = childrenMap[session.id].orEmpty()
            for (child in children) {
                collectDescendants(child, depth + 1, acc)
            }
        }

        for (root in rootSessions) {
            val items = mutableListOf<SessionTreeItem>()
            collectDescendants(root, 0, items)
            groups.add(SessionTreeGroup(root = root, items = items))
        }

        // Fallback for any orphaned sessions (e.g. circular refs)
        for (s in sessions) {
            if (s.id !in visited) {
                visited.add(s.id)
                groups.add(SessionTreeGroup(root = s, items = listOf(SessionTreeItem(s, 0))))
            }
        }

        return groups
    }

    private fun resolveParentMap(
        sessions: List<PiSessionBrief>,
        extraLineage: Map<String, String>,
        idMap: Map<String, PiSessionBrief>,
        fileMap: Map<String, PiSessionBrief>,
        fileBaseMap: Map<String, PiSessionBrief>,
    ): Map<String, String> {
        val parentMap = mutableMapOf<String, String>()

        // 1. Resolve from explicit parentSession field on PiSessionBrief
        for (s in sessions) {
            val pRef = s.parentSession?.trim().orEmpty()
            if (pRef.isNotEmpty()) {
                val matched = idMap[pRef]
                    ?: fileMap[normalizeFilePath(pRef)]
                    ?: fileBaseMap[pRef.substringAfterLast('/').substringAfterLast('\\')]
                if (matched != null && matched.id != s.id) {
                    parentMap[s.id] = matched.id
                }
            }
        }

        // 2. Resolve from persisted client-side lineage store
        for (s in sessions) {
            if (parentMap.containsKey(s.id)) continue
            val parentKey = extraLineage[s.id]
                ?: extraLineage[normalizeFilePath(s.file)]
                ?: extraLineage[s.file.substringAfterLast('/').substringAfterLast('\\')]
            if (!parentKey.isNullOrBlank()) {
                val matched = idMap[parentKey]
                    ?: fileMap[normalizeFilePath(parentKey)]
                    ?: fileBaseMap[parentKey.substringAfterLast('/').substringAfterLast('\\')]
                if (matched != null && matched.id != s.id) {
                    parentMap[s.id] = matched.id
                }
            }
        }

        // 3. Resolve from shared initial prompt / root preview heuristic within the same working directory
        val sessionsByCwd = sessions.groupBy { normalizeFilePath(it.cwd) }
        for ((cwd, cwdSessions) in sessionsByCwd) {
            if (cwd.isBlank() || cwdSessions.size < 2) continue

            val byPreview = cwdSessions.groupBy { s ->
                val p = s.preview?.trim()?.replace('\n', ' ')?.lowercase().orEmpty()
                if (p.length >= 10 && !isGenericPrompt(p)) p.take(45) else ""
            }

            for ((previewKey, cluster) in byPreview) {
                if (previewKey.isEmpty() || cluster.size < 2) continue

                val sortedCluster = cluster.sortedBy { it.timestamp.orEmpty() }
                val rootSession = sortedCluster.first()

                for (i in 1 until sortedCluster.size) {
                    val branchSession = sortedCluster[i]
                    if (!parentMap.containsKey(branchSession.id) && branchSession.id != rootSession.id) {
                        parentMap[branchSession.id] = rootSession.id
                    }
                }
            }
        }

        return parentMap
    }

    fun resolve(
        sessions: List<PiSessionBrief>,
        extraLineage: Map<String, String> = emptyMap(),
    ): Map<String, SessionRelationInfo> {
        if (sessions.isEmpty()) return emptyMap()

        val idMap = sessions.associateBy { it.id }
        val fileMap = sessions.associateBy { normalizeFilePath(it.file) }
        val fileBaseMap = sessions.associateBy { it.file.substringAfterLast('/').substringAfterLast('\\') }

        fun displayName(s: PiSessionBrief): String {
            return s.name?.takeIf { it.isNotBlank() }
                ?: s.preview?.takeIf { it.isNotBlank() }?.let { if (it.length > 20) it.take(20) + "…" else it }
                ?: "会话 ${s.id.take(8)}"
        }

        val parentMap = resolveParentMap(sessions, extraLineage, idMap, fileMap, fileBaseMap)

        // Build inverted child map: parentId -> list of childIds
        val childrenMap = mutableMapOf<String, MutableList<String>>()
        for ((childId, parentId) in parentMap) {
            childrenMap.getOrPut(parentId) { mutableListOf() }.add(childId)
        }

        // Helper to compute depth without infinite loops
        fun computeDepth(sessionId: String, visited: MutableSet<String> = mutableSetOf()): Int {
            if (!visited.add(sessionId)) return 0
            val pId = parentMap[sessionId] ?: return 0
            return 1 + computeDepth(pId, visited)
        }

        val result = mutableMapOf<String, SessionRelationInfo>()

        for (s in sessions) {
            val pId = parentMap[s.id]
            val children = childrenMap[s.id].orEmpty()

            if (pId != null) {
                val parentSession = idMap[pId]
                val parentName = parentSession?.let(::displayName) ?: "父级会话"
                val siblings = childrenMap[pId]?.filter { it != s.id }.orEmpty()
                val siblingNames = siblings.mapNotNull { idMap[it]?.let(::displayName) }
                val childNames = children.mapNotNull { idMap[it]?.let(::displayName) }
                val depth = computeDepth(s.id)

                result[s.id] = SessionRelationInfo(
                    kind = SessionRelationKind.BRANCH,
                    parentSessionId = pId,
                    parentFile = parentSession?.file,
                    parentDisplayName = parentName,
                    childCount = children.size,
                    childDisplayNames = childNames,
                    siblingCount = siblings.size,
                    siblingDisplayNames = siblingNames,
                    treeDepth = depth,
                )
            } else if (children.isNotEmpty()) {
                val childNames = children.mapNotNull { idMap[it]?.let(::displayName) }
                result[s.id] = SessionRelationInfo(
                    kind = SessionRelationKind.ROOT,
                    childCount = children.size,
                    childDisplayNames = childNames,
                    siblingCount = 0,
                    treeDepth = 0,
                )
            } else {
                result[s.id] = SessionRelationInfo(
                    kind = SessionRelationKind.STANDALONE,
                    childCount = 0,
                    siblingCount = 0,
                    treeDepth = 0,
                )
            }
        }

        return result
    }

    private fun normalizeFilePath(p: String?): String {
        return p?.trim()?.replace('\\', '/')?.trimEnd('/')?.lowercase() ?: ""
    }

    private fun isGenericPrompt(prompt: String): Boolean {
        val p = prompt.trim().lowercase()
        return p.startsWith("hi") ||
            p.startsWith("hello") ||
            p.startsWith("你好") ||
            p.startsWith("test") ||
            p.startsWith("ping") ||
            p.startsWith("/")
    }
}
