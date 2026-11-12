<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  Download,
  Eye,
  FileText,
} from '@lucide/vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import { katex } from '@mdit/plugin-katex'
import 'katex/dist/katex.min.css'

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  status: {
    type: String,
    default: 'complete'
  },
  attachments: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['download-attachment', 'preview-attachment'])

const renderedContent = ref('')
const expanded = ref(false)
const canCollapse = computed(() => props.status === 'complete' && props.content.length > 1600)
const imagePreview = ref(null)
const copiedCodeButton = ref(null)
let markdownRenderTimer = null
let copiedCodeTimer = null

hljs.registerLanguage('bash', bash)
hljs.registerLanguage('css', css)
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('python', python)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('yaml', yaml)

const markdownRenderer = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: true
})

markdownRenderer.use(katex, {
  throwOnError: false,
  strict: 'ignore'
})

markdownRenderer.renderer.rules.fence = (tokens, index) => {
  const token = tokens[index]
  const language = token.info.trim().split(/\s+/)[0].toLowerCase()
  const languageLabel = language || 'text'
  const highlightedCode = language && hljs.getLanguage(language)
    ? hljs.highlight(token.content, { language }).value
    : markdownRenderer.utils.escapeHtml(token.content)

  return `<div class="markdown-code-block">
    <div class="markdown-code-header">
      <span>${markdownRenderer.utils.escapeHtml(languageLabel)}</span>
      <button type="button" data-code-copy aria-label="复制代码">复制</button>
    </div>
    <pre><code class="hljs language-${markdownRenderer.utils.escapeHtml(languageLabel)}">${highlightedCode}</code></pre>
  </div>`
}

markdownRenderer.renderer.rules.table_open = () => '<div class="markdown-table-scroll"><table>'
markdownRenderer.renderer.rules.table_close = () => '</table></div>'

markdownRenderer.renderer.rules.link_open = (tokens, index, options, environment, self) => {
  const token = tokens[index]
  token.attrSet('target', '_blank')
  token.attrSet('rel', 'noopener noreferrer')
  return self.renderToken(tokens, index, options)
}

markdownRenderer.renderer.rules.image = (tokens, index) => {
  const token = tokens[index]
  const source = token.attrGet('src') || ''
  const safeSource = markdownRenderer.normalizeLink(source)
  const isSafeImage = /^(https?:\/\/|\/|\.\.?\/)/.test(safeSource)

  if (!isSafeImage || !markdownRenderer.validateLink(safeSource)) return ''

  return `<img
    class="markdown-image"
    src="${markdownRenderer.utils.escapeHtml(safeSource)}"
    alt="${markdownRenderer.utils.escapeHtml(token.content)}"
    loading="lazy"
    data-markdown-image
  >`
}

const renderedHtml = computed(() => markdownRenderer.render(renderedContent.value))
const isGenerating = computed(() => ['waiting', 'streaming', 'background'].includes(String(props.status).toLowerCase()) && !props.content)

function scheduleMarkdownRender() {
  if (markdownRenderTimer) window.clearTimeout(markdownRenderTimer)

  // Batch partial Markdown tokens so streaming does not repeatedly reparse incomplete syntax.
  if (props.status !== 'streaming') {
    renderedContent.value = props.content
    return
  }

  if (!renderedContent.value && props.content) {
    renderedContent.value = props.content
    return
  }

  markdownRenderTimer = window.setTimeout(() => {
    renderedContent.value = props.content
    markdownRenderTimer = null
  }, 80)
}

async function copyCode(button) {
  const code = button.closest('.markdown-code-block')?.querySelector('code')?.textContent
  if (!code) return

  try {
    await navigator.clipboard.writeText(code)
  } catch {
    const selection = window.getSelection()
    const range = document.createRange()
    const codeElement = button.closest('.markdown-code-block')?.querySelector('code')
    if (!codeElement || !selection) return
    range.selectNodeContents(codeElement)
    selection.removeAllRanges()
    selection.addRange(range)
    document.execCommand('copy')
    selection.removeAllRanges()
  }

  copiedCodeButton.value?.removeAttribute('data-copied')
  copiedCodeButton.value = button
  button.setAttribute('data-copied', 'true')

  if (copiedCodeTimer) window.clearTimeout(copiedCodeTimer)
  copiedCodeTimer = window.setTimeout(() => {
    button.removeAttribute('data-copied')
    copiedCodeButton.value = null
    copiedCodeTimer = null
  }, 1600)
}

function handleMarkdownClick(event) {
  const target = event.target
  if (!(target instanceof Element)) return

  const copyButton = target.closest('[data-code-copy]')
  if (copyButton) {
    copyCode(copyButton)
    return
  }

  const image = target.closest('img[data-markdown-image]')
  if (image) imagePreview.value = { src: image.currentSrc || image.src, alt: image.alt }
}

watch(
  [() => props.content, () => props.status],
  scheduleMarkdownRender,
  { immediate: true }
)

onBeforeUnmount(() => {
  if (markdownRenderTimer) window.clearTimeout(markdownRenderTimer)
  if (copiedCodeTimer) window.clearTimeout(copiedCodeTimer)
})
</script>

<template>
  <section class="ai-message-content">
    <div v-if="isGenerating" class="ai-generating-status" role="status" aria-live="polite">
      <span>正在思考</span>
    </div>

    <div
      v-if="renderedContent"
      class="markdown-content"
      :class="{ 'is-collapsed': canCollapse && !expanded, 'is-streaming': status === 'streaming' }"
      @click="handleMarkdownClick"
      v-html="renderedHtml"
    ></div>

    <button v-if="canCollapse" class="message-expand" type="button" :aria-expanded="expanded" @click="expanded = !expanded">
      {{ expanded ? '收起长回复' : '展开完整回复' }}
    </button>

    <section v-if="attachments.length" class="message-file-list" aria-label="相关文件">
      <article v-for="attachment in attachments" :key="attachment.id || attachment.name" class="message-file-card">
        <FileText :size="20" />
        <div class="message-file-copy">
          <strong>{{ attachment.name }}</strong>
          <span>{{ attachment.type || '文件' }}<template v-if="attachment.size"> · {{ attachment.size }}</template></span>
        </div>
        <div class="message-file-actions">
          <button type="button" aria-label="预览文件" data-tooltip="预览" @click="emit('preview-attachment', attachment)">
            <Eye :size="16" />
          </button>
          <button type="button" aria-label="下载文件" data-tooltip="下载" @click="emit('download-attachment', attachment)">
            <Download :size="16" />
          </button>
        </div>
      </article>
    </section>
  </section>

  <Teleport to="body">
    <Transition name="image-lightbox">
      <div v-if="imagePreview" class="image-lightbox" role="dialog" aria-modal="true" aria-label="图片预览" @pointerdown.self="imagePreview = null">
        <button type="button" class="image-lightbox-close" aria-label="关闭图片预览" @click="imagePreview = null">关闭</button>
        <img :src="imagePreview.src" :alt="imagePreview.alt" />
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* Markdown generates its own block spacing; inherited pre-wrap doubles it. */
.markdown-content { white-space: normal; min-width: 0; }
.markdown-content.is-collapsed { max-height: 460px; overflow: hidden; mask-image: linear-gradient(#000 88%, transparent); }
.message-expand { margin: 8px 0; padding: 6px 10px; border: 1px solid #dbe5ef; border-radius: 6px; background: #f8fbff; color: #2563eb; cursor: pointer; font: inherit; font-size: 12px; }
.message-expand:focus-visible { outline: 2px solid #2563eb; outline-offset: 2px; }

.ai-message-content {
  min-width: 0;
  color: #2b2f33;
}

.ai-generating-status {
  display: inline-flex;
  align-items: baseline;
  min-height: 22px;
  color: #7a8087;
  font-size: 13px;
  line-height: 1.5;
}


.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  margin: 24px 0 10px;
  color: inherit;
  font-weight: 760;
  line-height: 1.35;
}

.markdown-content :deep(h1) { font-size: 24px; }
.markdown-content :deep(h2) { font-size: 20px; }
.markdown-content :deep(h3) { font-size: 17px; }

.markdown-content :deep(h1:first-child),
.markdown-content :deep(h2:first-child),
.markdown-content :deep(h3:first-child) { margin-top: 0; }

.markdown-content :deep(p),
.markdown-content :deep(li) {
  color: inherit;
  font-size: 15px;
  line-height: 1.75;
  white-space: normal;
}

.markdown-content :deep(p) { margin: 0 0 12px; }
.markdown-content :deep(ul),
.markdown-content :deep(ol) { margin: 0 0 14px; padding-left: 22px; }
.markdown-content :deep(li + li) { margin-top: 4px; }

.markdown-content :deep(a) {
  color: #2563eb;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.markdown-content :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 12px;
  border-left: 3px solid #9cc1ec;
  background: #f7f9fc;
  color: #526174;
}

.markdown-content :deep(.markdown-table-scroll) {
  max-width: 100%;
  margin: 14px 0;
  overflow-x: auto;
  border: 1px solid #dfe6f0;
  border-radius: 8px;
}

.markdown-content :deep(table) {
  width: 100%;
  min-width: 480px;
  border-collapse: collapse;
  font-size: 14px;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 10px 12px;
  border-bottom: 1px solid #e6ebf2;
  text-align: left;
  vertical-align: top;
}

.markdown-content :deep(th) {
  background: #f5f7fa;
  color: #3d4d60;
  font-weight: 750;
}

.markdown-content :deep(tr:nth-child(even) td) { background: #fafbfd; }
.markdown-content :deep(tbody tr:hover td) { background: #eef5ff; }
.markdown-content :deep(tr:last-child td) { border-bottom: 0; }

.markdown-content :deep(.markdown-image) {
  display: block;
  max-width: 100%;
  max-height: 420px;
  margin: 14px 0;
  border-radius: 8px;
  cursor: zoom-in;
  object-fit: contain;
}

.markdown-content :deep(.markdown-code-block) {
  margin: 14px 0;
  overflow: hidden;
  border: 1px solid #dfe6f0;
  border-radius: 8px;
  background: #f8fafc;
}

.markdown-content :deep(.markdown-code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 34px;
  padding: 0 10px;
  border-bottom: 1px solid #e5eaf1;
  color: #65758a;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.markdown-content :deep(.markdown-code-header button) {
  min-height: 26px;
  padding: 0 7px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #4f6278;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
}

.markdown-content :deep(.markdown-code-header button:hover),
.markdown-content :deep(.markdown-code-header button[data-copied='true']) {
  background: #e8f0fe;
  color: #2563eb;
}

.markdown-content :deep(.markdown-code-header button[data-copied='true'])::after { content: '已复制'; }
.markdown-content :deep(.markdown-code-header button[data-copied='true']) { font-size: 0; }
.markdown-content :deep(.markdown-code-header button[data-copied='true'])::after { font-size: 12px; }

.markdown-content :deep(.markdown-code-block pre) {
  margin: 0;
  padding: 14px;
  overflow-x: auto;
}

.markdown-content :deep(.markdown-code-block code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.65;
}

.markdown-content :deep(.hljs-keyword),
.markdown-content :deep(.hljs-selector-tag),
.markdown-content :deep(.hljs-literal) { color: #7c3aed; }
.markdown-content :deep(.hljs-string),
.markdown-content :deep(.hljs-attr) { color: #0f766e; }
.markdown-content :deep(.hljs-number),
.markdown-content :deep(.hljs-params) { color: #b45309; }
.markdown-content :deep(.hljs-title),
.markdown-content :deep(.hljs-function) { color: #1d4ed8; }
.markdown-content :deep(.hljs-comment) { color: #7b8795; font-style: italic; }

.markdown-content :deep(.katex-block) { overflow-x: auto; overflow-y: hidden; }

.message-file-list {
  display: grid;
  gap: 8px;
  max-width: 560px;
  margin-top: 14px;
}

.message-file-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #dfe6f0;
  border-radius: 8px;
  background: #fbfcfe;
  color: #526174;
}

.message-file-copy { min-width: 0; flex: 1; }
.message-file-copy strong,
.message-file-copy span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.message-file-copy strong { color: #344154; font-size: 13px; }
.message-file-copy span { margin-top: 2px; color: #718096; font-size: 12px; }

.message-file-actions { display: flex; gap: 2px; }
.message-file-actions button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #627287;
}
.message-file-actions button:hover { background: #e8f0fe; color: #2563eb; }

.image-lightbox {
  position: fixed;
  inset: 0;
  z-index: 1300;
  display: grid;
  place-items: center;
  padding: 32px;
  background: rgba(15, 23, 42, 0.78);
}

.image-lightbox img { max-width: min(100%, 1100px); max-height: calc(100vh - 64px); border-radius: 8px; object-fit: contain; }
.image-lightbox-close {
  position: fixed;
  top: 18px;
  right: 18px;
  min-height: 34px;
  padding: 0 12px;
  border: 0;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
  font: inherit;
}
.image-lightbox-close:hover { background: rgba(255, 255, 255, 0.26); }

.image-lightbox-enter-active,
.image-lightbox-leave-active { transition: opacity 180ms ease; }
.image-lightbox-enter-from,
.image-lightbox-leave-to { opacity: 0; }

:global(html[data-theme='dark']) .ai-message-content { color: #e7edf5; }
:global(html[data-theme='dark']) .ai-generating-status { color: #aeb8c4; }
:global(html[data-theme='dark']) .markdown-content :deep(a) { color: #91c0ff; }
:global(html[data-theme='dark']) .markdown-content :deep(blockquote) { border-left-color: #5f8fd1; background: rgba(255, 255, 255, 0.05); color: #bdc9d8; }
:global(html[data-theme='dark']) .markdown-content :deep(.markdown-table-scroll),
:global(html[data-theme='dark']) .markdown-content :deep(.markdown-code-block),
:global(html[data-theme='dark']) .message-file-card { border-color: #344455; background: #1b2836; }
:global(html[data-theme='dark']) .markdown-content :deep(th) { background: #243342; color: #e7edf5; }
:global(html[data-theme='dark']) .markdown-content :deep(td),
:global(html[data-theme='dark']) .markdown-content :deep(th) { border-color: #344455; }
:global(html[data-theme='dark']) .markdown-content :deep(tr:nth-child(even) td) { background: rgba(255, 255, 255, 0.025); }
:global(html[data-theme='dark']) .markdown-content :deep(tbody tr:hover td) { background: rgba(145, 192, 255, 0.12); }
:global(html[data-theme='dark']) .markdown-content :deep(.markdown-code-header) { border-color: #344455; color: #aebdcd; }
:global(html[data-theme='dark']) .markdown-content :deep(.markdown-code-header button) { color: #bdc9d8; }
:global(html[data-theme='dark']) .markdown-content :deep(.markdown-code-header button:hover),
:global(html[data-theme='dark']) .markdown-content :deep(.markdown-code-header button[data-copied='true']) { background: rgba(145, 192, 255, 0.16); color: #91c0ff; }
:global(html[data-theme='dark']) .markdown-content :deep(.hljs-keyword),
:global(html[data-theme='dark']) .markdown-content :deep(.hljs-selector-tag),
:global(html[data-theme='dark']) .markdown-content :deep(.hljs-literal) { color: #c4b5fd; }
:global(html[data-theme='dark']) .markdown-content :deep(.hljs-string),
:global(html[data-theme='dark']) .markdown-content :deep(.hljs-attr) { color: #5eead4; }
:global(html[data-theme='dark']) .markdown-content :deep(.hljs-number),
:global(html[data-theme='dark']) .markdown-content :deep(.hljs-params) { color: #fcd34d; }
:global(html[data-theme='dark']) .markdown-content :deep(.hljs-title),
:global(html[data-theme='dark']) .markdown-content :deep(.hljs-function) { color: #91c0ff; }
:global(html[data-theme='dark']) .markdown-content :deep(.hljs-comment) { color: #91a0b1; }
:global(html[data-theme='dark']) .message-file-copy strong { color: #e7edf5; }
:global(html[data-theme='dark']) .message-file-copy span,
:global(html[data-theme='dark']) .message-file-card { color: #aebdcd; }
:global(html[data-theme='dark']) .message-file-actions button { color: #bdc9d8; }
:global(html[data-theme='dark']) .message-file-actions button:hover { background: rgba(145, 192, 255, 0.16); color: #91c0ff; }

@media (max-width: 640px) {
  .markdown-content :deep(h1) { font-size: 21px; }
  .markdown-content :deep(h2) { font-size: 18px; }
  .markdown-content :deep(h3) { font-size: 16px; }
  .markdown-content :deep(.markdown-code-block pre) { padding: 12px; }
  .image-lightbox { padding: 16px; }
}

@media (prefers-reduced-motion: reduce) {
  .image-lightbox-enter-active,
  .image-lightbox-leave-active { transition: none; }
}
</style>
