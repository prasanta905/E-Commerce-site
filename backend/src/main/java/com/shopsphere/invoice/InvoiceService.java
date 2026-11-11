package com.shopsphere.invoice;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import com.shopsphere.common.exception.BusinessException;
import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.repository.OrderRepository;

/**
 * Order invoice PDF (OpenPDF). Owner-verified by the caller; only final-status
 * orders may download one (CONFIRMED onwards — not PENDING/CANCELLED).
 */
@Service
public class InvoiceService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(IST);

    private static final Font TITLE = new Font(Font.HELVETICA, 20, Font.BOLD);
    private static final Font HEADING = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font BODY = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font SMALL = new Font(Font.HELVETICA, 9, Font.NORMAL);

    private final OrderRepository orderRepository;

    public InvoiceService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public byte[] renderForUser(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> ResourceNotFound.of("Order")); // never hint at others' orders
        return render(order);
    }

    @Transactional(readOnly = true)
    public byte[] render(Order order) {
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(409, "INVOICE_UNAVAILABLE",
                    "Invoices are available once the order is confirmed");
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("ShopSphere", TITLE));
            document.add(new Paragraph("Tax Invoice / Order Receipt", BODY));
            document.add(spacer(12));

            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.addCell(cell("Invoice for order " + order.getOrderNumber(), HEADING));
            headerTable.addCell(cell("Placed: " + DATE_FMT.format(order.getCreatedAt()), BODY));
            headerTable.addCell(cell("Status: " + order.getStatus(), BODY));
            headerTable.addCell(cell("Payment: " + order.getPaymentMethod(), BODY));
            document.add(headerTable);

            document.add(spacer(10));
            document.add(new Paragraph("Billed to", HEADING));
            document.add(new Paragraph(addressLines(order.getAddressSnapshot()), BODY));

            document.add(spacer(10));
            PdfPTable items = new PdfPTable(new float[]{5, 1, 2, 2});
            items.setWidthPercentage(100);
            items.addCell(cell("Item", HEADING));
            items.addCell(cell("Qty", HEADING));
            items.addCell(cell("Unit price", HEADING));
            items.addCell(cell("Line total", HEADING));
            for (var item : order.getItems()) {
                items.addCell(cell(item.getProductName(), BODY));
                items.addCell(cell(String.valueOf(item.getQuantity()), BODY));
                items.addCell(cell("Rs. " + item.getUnitPrice(), BODY));
                items.addCell(cell("Rs. " + item.getLineTotal(), BODY));
            }
            document.add(items);

            document.add(spacer(10));
            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(60);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.addCell(cell("Subtotal", HEADING));
            totals.addCell(cell("Rs. " + order.getSubtotal(), BODY));
            totals.addCell(cell("Discount", HEADING));
            totals.addCell(cell("- Rs. " + order.getDiscountAmount(), BODY));
            totals.addCell(cell("Shipping", HEADING));
            totals.addCell(cell("Rs. " + order.getShippingFee(), BODY));
            totals.addCell(cell("Total", HEADING));
            totals.addCell(cell("Rs. " + order.getTotalAmount(), HEADING));
            document.add(totals);

            document.add(spacer(14));
            document.add(new Paragraph(
                    "Thank you for shopping with ShopSphere. This is a system-generated "
                            + "invoice for a demonstration project (test-mode payments).", SMALL));

            document.close();
            return out.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invoice rendering failed", e);
        }
    }

    private String addressLines(String snapshotJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(snapshotJson, Map.class);
            return (orDash(map.get("contactName")) + "\n"
                    + orDash(map.get("line1")) + (isBlank(map.get("line2")) ? "" : ", " + map.get("line2")) + "\n"
                    + orDash(map.get("city")) + ", " + orDash(map.get("state")) + " - " + orDash(map.get("pincode"))
                    + "\nPhone: " + orDash(map.get("phone")));
        } catch (Exception e) {
            return "Address on file";
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(org.openpdf.text.Rectangle.NO_BORDER);
        cell.setPadding(4);
        return cell;
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }
}
