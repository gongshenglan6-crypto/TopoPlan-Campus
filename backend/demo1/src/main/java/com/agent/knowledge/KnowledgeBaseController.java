package com.agent.knowledge;

import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeBaseController {
    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) { this.service = service; }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() { return new ApiResponse<>(service.overview()); }

    @GetMapping("/documents")
    public ApiResponse<ListResponse<Map<String, Object>>> documents() { List<Map<String, Object>> items = service.listDocuments(); return new ApiResponse<>(ListResponse.of(items)); }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(defaultValue = "网络基础") String category,
                                                    @RequestParam(defaultValue = "系统管理员") String uploader) {
        return new ApiResponse<>(service.upload(file, category, uploader));
    }

    @PostMapping("/documents/{id}/reparse")
    public ApiResponse<Map<String, Object>> reparse(@PathVariable Long id) { return new ApiResponse<>(service.reparse(id)); }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return new ApiResponse<>(null); }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        KnowledgeDocument document = service.getDocument(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(document.getName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(document.getOriginalContent());
    }

    @GetMapping(value = "/documents/{id}/view", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> view(@PathVariable Long id) {
        KnowledgeDocument document = service.getDocument(id);
        String title = HtmlUtils.htmlEscape(document.getName());
        String content = document.getExtractedText().isBlank() ? "该文档暂无可查看的解析文本，请重新解析后再试。" : document.getExtractedText();
        String page = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + title + "</title><style>body{margin:0;background:#f5f7fb;color:#1f2937;font:14px/1.8 -apple-system,BlinkMacSystemFont,\"Segoe UI\",\"Microsoft YaHei\",sans-serif}.page{max-width:960px;margin:32px auto;padding:0 20px}.card{overflow:hidden;border:1px solid #e2e8f0;border-radius:12px;background:#fff;box-shadow:0 12px 30px rgba(15,23,42,.06)}header{padding:20px 24px;border-bottom:1px solid #e2e8f0}h1{margin:0;font-size:20px}p{margin:6px 0 0;color:#64748b;font-size:12px}pre{margin:0;padding:24px;white-space:pre-wrap;word-break:break-word;font:13px/1.85 ui-monospace,SFMono-Regular,Consolas,monospace}@media(max-width:640px){.page{margin:16px auto;padding:0 12px}header,pre{padding:16px}}</style></head><body><main class=\"page\"><article class=\"card\"><header><h1>" + title + "</h1><p>知识库已入库文本 · " + HtmlUtils.htmlEscape(document.getCategory()) + "</p></header><pre>" + HtmlUtils.htmlEscape(content) + "</pre></article></main></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page);
    }

    @GetMapping("/rag-config")
    public ApiResponse<Map<String, Object>> ragConfig() { return new ApiResponse<>(service.ragConfig()); }

    @PutMapping("/rag-config")
    public ApiResponse<Map<String, Object>> saveRagConfig(@RequestBody Map<String, Object> value) { return new ApiResponse<>(service.saveRagConfig(value)); }

    @PostMapping("/retrieval-test")
    public ApiResponse<Map<String, Object>> retrievalTest(@RequestBody Map<String, Object> value) { return new ApiResponse<>(service.retrieve(value)); }
}
