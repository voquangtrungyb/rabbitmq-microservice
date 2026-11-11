import { expect, test } from '@playwright/test';

test.describe('RabbitMQ Microservices - Zero Bottleneck & High Reliability', () => {

  test.beforeEach(async ({ page }) => {
    
    await page.goto('http://localhost:8080', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('h1')).toContainText('RabbitMQ Microservices Demo');
  });

  test('E2E-01: Đặt hàng thành công phản hồi tức thì HTTP 202 Accepted và 2 Consumer đồng thời ACK', async ({ page }) => {
    
    await page.fill('#customerName', 'Võ Quang Trung');
    await page.fill('#email', 'trungvq@fpt.edu.vn');
    await page.fill('#productName', 'MacBook Pro M3 Max');
    await page.fill('#quantity', '1');
    await page.fill('#amount', '3500.0');

    await page.click('#btnSubmitOrder');

    const statusBadge = page.locator('#responseStatus');
    await expect(statusBadge).toContainText('202 Accepted');

    const logContainer = page.locator('#consumerLogs');
    await expect(logContainer).toContainText('EMAIL-CONSUMER', { timeout: 6000 });
    await expect(logContainer).toContainText('INVENTORY-CONSUMER', { timeout: 6000 });
    await expect(logContainer).toContainText('Manual ACK', { timeout: 6000 });
  });

  test('E2E-02: Cô lập lỗi tồn kho - Kích hoạt Dead-Letter Queue (DLQ) an toàn không gây nghẽn', async ({ page }) => {
    
    await page.fill('#customerName', 'Võ Quang Trung (Test Lỗi Kho)');
    await page.fill('#email', 'trungvq@fpt.edu.vn');
    await page.fill('#productName', 'MacBook Pro ERROR (Hỏng Kho)');
    await page.fill('#quantity', '-1');
    await page.fill('#amount', '3500.0');

    await page.click('#btnSubmitOrder');

    const statusBadge = page.locator('#responseStatus');
    await expect(statusBadge).toContainText('202 Accepted');

    const logContainer = page.locator('#consumerLogs');
    await expect(logContainer).toContainText('NACK (Requeue: false)', { timeout: 6000 });
    await expect(logContainer).toContainText('order.dlq', { timeout: 6000 });

    await page.waitForTimeout(5000);
  });

});

