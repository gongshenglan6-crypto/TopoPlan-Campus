import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('knowledge module separates overview, file maintenance, RAG configuration and retrieval testing', async () => {
  const [overview, documents, ragConfig, retrievalTest, styles] = await Promise.all([
    readFile(new URL('../src/components/admin/AdminKnowledgeBase.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/admin/AdminKnowledgeDocuments.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/admin/AdminRagConfig.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/admin/AdminKnowledgeTest.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/styles.css', import.meta.url), 'utf8')
  ])

  assert.match(overview, /知识分类分布/)
  assert.match(overview, /知识库运行状态/)
  assert.match(overview, /最近知识库活动/)
  assert.match(overview, /进入文档管理/)
  assert.doesNotMatch(overview, /admin-knowledge-table/)
  assert.match(documents, /文档名称/)
  assert.match(documents, /重新解析/)
  assert.match(documents, /快速|跳至/)
  assert.match(ragConfig, /检索策略/)
  assert.match(ragConfig, /上下文与重排/)
  assert.match(ragConfig, /文档分块/)
  assert.match(ragConfig, /rag-custom-select/)
  assert.match(ragConfig, /rag-action-bar/)
  assert.match(retrievalTest, /检索结果概览|检索耗时/)
  assert.match(retrievalTest, /RAG 最终上下文/)
  assert.match(styles, /\.admin-document-actions \.admin-knowledge-download-button/)
})

test('knowledge base confirms destructive document deletion before calling the API', async () => {
  const [knowledge, styles] = await Promise.all([
    readFile(new URL('../src/components/admin/AdminKnowledgeDocuments.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/styles.css', import.meta.url), 'utf8')
  ])

  assert.match(knowledge, /const pendingDeleteDocument = ref\(null\)/)
  assert.match(knowledge, /title="删除文档"/)
  assert.match(knowledge, /@click="requestDocumentDeletion\(document\)"/)
  assert.match(knowledge, /删除知识文档/)
  assert.match(knowledge, /确定删除“\{\{ pendingDeleteDocument\.name \}\}”吗？[\s\S]*该文档对应的知识块和向量索引数据也会被移除，此操作不可撤销。/)
  assert.match(knowledge, /class="admin-danger-button"[^>]*@click="confirmDocumentDeletion"/)
  assert.match(knowledge, /<Trash2 :size="14" \/>/)
  assert.match(styles, /\.admin-document-actions \.admin-knowledge-download-button,\s*\.admin-document-actions \.admin-knowledge-delete-button\s*\{[^}]*border:\s*1px solid #e2e8f0;[^}]*border-radius:\s*7px;[^}]*background:\s*#ffffff;/s)
  assert.match(styles, /\.admin-document-actions \.admin-knowledge-download-button,\s*\.admin-document-actions \.admin-knowledge-delete-button\s*\{[^}]*flex-shrink:\s*0;/s)
  assert.match(styles, /\.admin-document-actions \.admin-knowledge-delete-button\s*\{[^}]*width:\s*70px;[^}]*height:\s*auto;[^}]*padding:\s*0 10px;[^}]*color:\s*#475569;/s)
  assert.match(styles, /\.admin-document-actions \.admin-knowledge-delete-button\s*\{[^}]*font-size:\s*12px;[^}]*font-weight:\s*600;/s)
  assert.match(styles, /\.admin-document-actions \.admin-knowledge-delete-button\s*\{[^}]*align-self:\s*stretch;/s)
  assert.match(styles, /\.admin-document-actions \.admin-knowledge-download-button:hover,\s*\.admin-document-actions \.admin-knowledge-delete-button:hover,\s*\.admin-document-actions \.admin-knowledge-delete-button:focus-visible\s*\{[^}]*border-color:\s*#cbd5e1;[^}]*background:\s*#f8fafc;[^}]*color:\s*#1f2937;/s)
  assert.match(styles, /html\[data-theme='dark'\] \.admin-document-actions \.admin-knowledge-download-button:hover,\s*html\[data-theme='dark'\] \.admin-document-actions \.admin-knowledge-delete-button:hover\s*\{/s)
  assert.doesNotMatch(styles, /\.admin-knowledge-delete-button:hover,\s*\.admin-knowledge-delete-button:focus-visible\s*\{[^}]*#fecaca/s)
})
