package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PaymentOrderView;
import com.zxylearn.build_guard_server.service.FinePaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private static final DateTimeFormatter PAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final FinePaymentService finePaymentService;

    public PaymentController(FinePaymentService finePaymentService) {
        this.finePaymentService = finePaymentService;
    }

    @PostMapping("/fines/{violationId}/refresh")
    public ApiResponse<PaymentOrderView> refreshFinePaymentQr(@PathVariable long violationId) {
        return ApiResponse.ok(finePaymentService.refreshPaymentQr(violationId));
    }

    @PostMapping("/fines/{violationId}/refund")
    public ApiResponse<Void> refundFine(@PathVariable long violationId) {
        finePaymentService.refund(violationId);
        return ApiResponse.ok();
    }

    @PostMapping("/fines/{violationId}/sync")
    public ApiResponse<PaymentOrderView> syncFinePayment(@PathVariable long violationId) {
        return ApiResponse.ok(finePaymentService.syncPaymentStatus(violationId));
    }

    @GetMapping(value = "/fines/{violationId}/pay", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> finePaymentPage(@PathVariable long violationId) {
        PaymentOrderView order = finePaymentService.currentOrCreatePaymentQr(violationId);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .body(renderPaymentPage(order));
    }

    @GetMapping(value = "/fines/{violationId}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> finePaymentQrPng(@PathVariable long violationId,
                                                   @RequestParam(required = false) Long orderId) {
        byte[] png = finePaymentService.paymentQrPng(violationId, orderId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                .body(png);
    }

    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        boolean handled = finePaymentService.handleAlipayNotify(params);
        log.info("alipay notify received outTradeNo={} tradeStatus={} handled={}",
                params.get("out_trade_no"),
                params.get("trade_status"),
                handled);
        return handled ? "success" : "failure";
    }

    private String renderPaymentPage(PaymentOrderView order) {
        boolean paid = Integer.valueOf(1).equals(order.payStatus());
        String qrUrl = !paid && order.id() != null
                ? "/api/payments/fines/" + order.violationId() + "/qr.png?orderId=" + order.id()
                : "";
        String expireAt = order.expireAt() == null ? "-" : PAGE_TIME_FORMATTER.format(order.expireAt());
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>BuildGuard 罚款支付</title>
                  <style>
                    *{box-sizing:border-box}body{margin:0;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",Arial,sans-serif;background:#eef4fb;color:#25364d}
                    .page{min-height:100vh;display:grid;place-items:center;padding:32px 16px}.card{width:min(920px,100%%);background:#fff;border:1px solid #dbe6f3;border-radius:18px;box-shadow:0 18px 50px rgba(30,58,95,.13);overflow:hidden}
                    .header{padding:26px 32px;background:#1f3b61;color:#fff}.header h1{margin:0;font-size:28px;letter-spacing:0}.header p{margin:8px 0 0;color:#cfe0f5}
                    .body{display:grid;grid-template-columns:340px minmax(0,1fr);gap:30px;padding:32px}.qr{display:grid;place-items:center;gap:16px;padding:24px;background:#f7faff;border:1px solid #dbe6f3;border-radius:14px;min-height:390px}
                    .qr img{width:280px;height:280px;border:10px solid #fff;border-radius:10px;box-shadow:0 8px 24px rgba(36,70,112,.12)}.paid{display:grid;place-items:center;width:280px;height:280px;border-radius:14px;background:#e9fbf0;color:#16a34a;font-size:30px;font-weight:800}
                    .qr span{font-size:15px;color:#667892}.info h2{margin:0 0 18px;font-size:24px}.rows{display:grid;gap:14px}.row{display:grid;grid-template-columns:96px minmax(0,1fr);gap:14px;padding-bottom:12px;border-bottom:1px solid #edf2f7}
                    dt{color:#74859c}dd{margin:0;font-weight:700;word-break:break-all}.amount{font-size:26px;color:#e24a3b}.status{display:inline-flex;width:max-content;padding:5px 14px;border-radius:999px;background:#fee2e2;color:#ef4444}.status.ok{background:#dcfce7;color:#16a34a}
                    .actions{display:flex;gap:12px;flex-wrap:wrap;margin-top:26px}button{height:44px;padding:0 20px;border:0;border-radius:10px;font-size:16px;font-weight:700;cursor:pointer}
                    .primary{background:#3f6fea;color:#fff}.secondary{background:#eef3fa;color:#26405f}.message{min-height:24px;margin-top:16px;color:#64748b}.error{color:#dc2626}
                    @media(max-width:760px){.body{grid-template-columns:1fr;padding:22px}.header{padding:22px}.qr{min-height:auto}.row{grid-template-columns:82px minmax(0,1fr)}}
                  </style>
                </head>
                <body>
                  <main class="page">
                    <section class="card">
                      <header class="header">
                        <h1>BuildGuard 罚款支付</h1>
                        <p>请使用支付宝沙箱 App 扫码支付；二维码失效时可刷新。</p>
                      </header>
                      <section class="body">
                        <div class="qr">
                          <img id="qrImage" src="%s" alt="支付宝罚款支付二维码" style="%s">
                          <div id="paidBox" class="paid" style="%s">已支付</div>
                          <span id="qrHint">%s</span>
                        </div>
                        <div class="info">
                          <h2>支付订单</h2>
                          <dl class="rows">
                            <div class="row"><dt>罚款记录</dt><dd id="violationId">#%d</dd></div>
                            <div class="row"><dt>订单编号</dt><dd id="outTradeNo">%s</dd></div>
                            <div class="row"><dt>订单标题</dt><dd id="subject">%s</dd></div>
                            <div class="row"><dt>支付金额</dt><dd id="amount" class="amount">%s 元</dd></div>
                            <div class="row"><dt>过期时间</dt><dd id="expireAt">%s</dd></div>
                            <div class="row"><dt>支付状态</dt><dd><span id="status" class="status %s">%s</span></dd></div>
                          </dl>
                          <div class="actions">
                            <button class="primary" type="button" id="refreshBtn">刷新二维码</button>
                            <button class="secondary" type="button" id="syncBtn">查询支付状态</button>
                          </div>
                          <div id="message" class="message"></div>
                        </div>
                      </section>
                    </section>
                  </main>
                  <script>
                    const violationId = %d;
                    const api = async (path) => {
                      const response = await fetch(path, { method: 'POST' });
                      const payload = await response.json();
                      if (!response.ok || payload.code !== 200) throw new Error(payload.message || '请求失败');
                      return payload.data;
                    };
                    const text = (value) => value == null || value === '' ? '-' : String(value);
                    const money = (value) => Number(value || 0).toFixed(2) + ' 元';
                    const statusText = (status) => status === 1 ? '已支付' : status === 2 ? '已退款' : status === 3 ? '已撤销' : '未支付';
                    const setMessage = (message, error=false) => {
                      const box = document.getElementById('message');
                      box.textContent = message || '';
                      box.className = error ? 'message error' : 'message';
                    };
                    const render = (order) => {
                      const paid = order.payStatus === 1;
                      document.getElementById('outTradeNo').textContent = text(order.outTradeNo);
                      document.getElementById('subject').textContent = text(order.subject);
                      document.getElementById('amount').textContent = money(order.amount);
                      document.getElementById('expireAt').textContent = order.expireAt ? order.expireAt.replace('T', ' ').slice(0, 19) : '-';
                      const status = document.getElementById('status');
                      status.textContent = statusText(order.payStatus);
                      status.className = paid ? 'status ok' : 'status';
                      const image = document.getElementById('qrImage');
                      const paidBox = document.getElementById('paidBox');
                      image.style.display = paid || !order.id ? 'none' : 'block';
                      paidBox.style.display = paid ? 'grid' : 'none';
                      if (!paid && order.id) image.src = `/api/payments/fines/${violationId}/qr.png?orderId=${order.id}&t=${Date.now()}`;
                      document.getElementById('qrHint').textContent = paid ? '支付完成，系统已记录罚款状态' : '请使用支付宝沙箱 App 扫码支付';
                    };
                    document.getElementById('refreshBtn').addEventListener('click', async () => {
                      try { render(await api(`/api/payments/fines/${violationId}/refresh`)); setMessage('二维码已刷新'); }
                      catch (e) { setMessage(e.message, true); }
                    });
                    document.getElementById('syncBtn').addEventListener('click', async () => {
                      try { render(await api(`/api/payments/fines/${violationId}/sync`)); setMessage('支付状态已更新'); }
                      catch (e) { setMessage(e.message, true); }
                    });
                    setInterval(async () => {
                      try { render(await api(`/api/payments/fines/${violationId}/sync`)); } catch (e) {}
                    }, 3000);
                  </script>
                </body>
                </html>
                """.formatted(
                escapeAttr(qrUrl),
                paid ? "display:none" : "",
                paid ? "" : "display:none",
                paid ? "支付完成，系统已记录罚款状态" : "请使用支付宝沙箱 App 扫码支付",
                order.violationId(),
                escapeHtml(order.outTradeNo()),
                escapeHtml(order.subject()),
                formatAmount(order.amount()),
                escapeHtml(expireAt),
                paid ? "ok" : "",
                paid ? "已支付" : payStatusText(order.payStatus()),
                order.violationId()
        );
    }

    private String payStatusText(Integer status) {
        if (Integer.valueOf(1).equals(status)) {
            return "已支付";
        }
        if (Integer.valueOf(2).equals(status)) {
            return "已退款";
        }
        if (Integer.valueOf(3).equals(status)) {
            return "已撤销";
        }
        return "未支付";
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeAttr(String value) {
        return value == null ? "" : escapeHtml(value);
    }
}
