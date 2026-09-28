package com.piremote.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRelationResolverTest {

    @Test
    fun testRootAndSiblingBranchesResolution() {
        val root = PiSessionBrief(
            file = "/home/user/.pi/agent/sessions/proj/root.jsonl",
            id = "sess-root",
            cwd = "/home/user/project",
            name = "主会话",
            preview = "帮我重构网络层架构并编写单元测试",
            timestamp = "2026-09-24T10:00:00Z",
            messageCount = 10,
        )

        val branch1 = PiSessionBrief(
            file = "/home/user/.pi/agent/sessions/proj/branch1.jsonl",
            id = "sess-b1",
            cwd = "/home/user/project",
            name = "分支1",
            preview = "帮我重构网络层架构并编写单元测试",
            timestamp = "2026-09-24T10:30:00Z",
            messageCount = 5,
            parentSession = "sess-root",
        )

        val branch2 = PiSessionBrief(
            file = "/home/user/.pi/agent/sessions/proj/branch2.jsonl",
            id = "sess-b2",
            cwd = "/home/user/project",
            name = "分支2",
            preview = "帮我重构网络层架构并编写单元测试",
            timestamp = "2026-09-24T11:00:00Z",
            messageCount = 7,
            parentSession = "sess-root",
        )

        val standalone = PiSessionBrief(
            file = "/home/user/.pi/agent/sessions/proj/other.jsonl",
            id = "sess-other",
            cwd = "/home/user/project",
            name = "独立会话",
            preview = "修复打包脚本中的路径错误问题",
            timestamp = "2026-09-24T12:00:00Z",
            messageCount = 3,
        )

        val sessions = listOf(root, branch1, branch2, standalone)
        val relations = SessionRelationResolver.resolve(sessions)

        // Verify root
        val rootRel = relations["sess-root"]
        assertNotNull(rootRel)
        assertEquals(SessionRelationKind.ROOT, rootRel!!.kind)
        assertEquals(2, rootRel.childCount)

        // Verify branch 1
        val b1Rel = relations["sess-b1"]
        assertNotNull(b1Rel)
        assertEquals(SessionRelationKind.BRANCH, b1Rel!!.kind)
        assertEquals("sess-root", b1Rel.parentSessionId)
        assertEquals("主会话", b1Rel.parentDisplayName)
        assertEquals(1, b1Rel.siblingCount)
        assertTrue(b1Rel.siblingDisplayNames.contains("分支2"))

        // Verify branch 2
        val b2Rel = relations["sess-b2"]
        assertNotNull(b2Rel)
        assertEquals(SessionRelationKind.BRANCH, b2Rel!!.kind)
        assertEquals("sess-root", b2Rel.parentSessionId)
        assertEquals("主会话", b2Rel.parentDisplayName)
        assertEquals(1, b2Rel.siblingCount)
        assertTrue(b2Rel.siblingDisplayNames.contains("分支1"))

        // Verify standalone
        val otherRel = relations["sess-other"]
        assertNotNull(otherRel)
        assertEquals(SessionRelationKind.STANDALONE, otherRel!!.kind)
    }

    @Test
    fun testLineageMapAndHeuristicResolution() {
        val root = PiSessionBrief(
            file = "/home/user/.pi/agent/sessions/proj/root.jsonl",
            id = "sess-alpha",
            cwd = "/home/user/project",
            name = "认证模块实现",
            preview = "使用 JWT 实现双向鉴权并提供 Token 刷新接口",
            timestamp = "2026-09-24T09:00:00Z",
            messageCount = 12,
        )

        // Has same preview in same cwd, should be resolved as branch via heuristic
        val childHeuristic = PiSessionBrief(
            file = "/home/user/.pi/agent/sessions/proj/child.jsonl",
            id = "sess-beta",
            cwd = "/home/user/project",
            name = "Session 2",
            preview = "使用 JWT 实现双向鉴权并提供 Token 刷新接口",
            timestamp = "2026-09-24T09:40:00Z",
            messageCount = 4,
        )

        val sessions = listOf(root, childHeuristic)
        val relations = SessionRelationResolver.resolve(sessions)

        val rootRel = relations["sess-alpha"]
        val childRel = relations["sess-beta"]

        assertNotNull(rootRel)
        assertEquals(SessionRelationKind.ROOT, rootRel!!.kind)
        assertEquals(1, rootRel.childCount)

        assertNotNull(childRel)
        assertEquals(SessionRelationKind.BRANCH, childRel!!.kind)
        assertEquals("sess-alpha", childRel.parentSessionId)
        assertEquals("认证模块实现", childRel.parentDisplayName)
    }

    @Test
    fun testBuildTreeGroups() {
        val root = PiSessionBrief(
            file = "/proj/root.jsonl",
            id = "root-1",
            cwd = "/proj",
            name = "主干",
            preview = "测试长提示词内容ABCD1234567890",
            timestamp = "2026-09-24T10:00:00Z",
            messageCount = 10,
        )
        val branch1 = PiSessionBrief(
            file = "/proj/b1.jsonl",
            id = "b-1",
            cwd = "/proj",
            name = "分支1",
            preview = "测试长提示词内容ABCD1234567890",
            timestamp = "2026-09-24T10:10:00Z",
            messageCount = 4,
            parentSession = "root-1",
        )
        val subBranch1 = PiSessionBrief(
            file = "/proj/sub1.jsonl",
            id = "sub-1",
            cwd = "/proj",
            name = "分支1的子分支",
            preview = "测试长提示词内容ABCD1234567890",
            timestamp = "2026-09-24T10:20:00Z",
            messageCount = 2,
            parentSession = "b-1",
        )
        val branch2 = PiSessionBrief(
            file = "/proj/b2.jsonl",
            id = "b-2",
            cwd = "/proj",
            name = "分支2",
            preview = "测试长提示词内容ABCD1234567890",
            timestamp = "2026-09-24T10:15:00Z",
            messageCount = 5,
            parentSession = "root-1",
        )

        val groups = SessionRelationResolver.buildTreeGroups(listOf(root, branch1, subBranch1, branch2))
        assertEquals(1, groups.size)
        val items = groups[0].items
        assertEquals(4, items.size)

        // root at depth 0
        assertEquals("root-1", items[0].session.id)
        assertEquals(0, items[0].depth)

        // branch 1 at depth 1
        assertEquals("b-1", items[1].session.id)
        assertEquals(1, items[1].depth)

        // sub-branch of branch 1 at depth 2
        assertEquals("sub-1", items[2].session.id)
        assertEquals(2, items[2].depth)

        // branch 2 at depth 1 (sibling branch to branch 1)
        assertEquals("b-2", items[3].session.id)
        assertEquals(1, items[3].depth)
    }
}
