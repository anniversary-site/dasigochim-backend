package com.dasigochim.file.hwp;

import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.file.storage.FileStorageService;
import com.dasigochim.photo.domain.ReportPhoto;
import com.dasigochim.photo.repository.ReportPhotoRepository;
import com.dasigochim.report.domain.Report;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.CtrlHeaderGso;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.HeightCriterion;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.HorzRelTo;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.ObjectNumberSort;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.RelativeArrange;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.TextFlowMethod;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.TextHorzArrange;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.VertRelTo;
import kr.dogfoot.hwplib.object.bodytext.control.ctrlheader.gso.WidthCriterion;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlPicture;
import kr.dogfoot.hwplib.object.bodytext.control.gso.GsoControlType;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.ShapeComponentNormal;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineArrowShape;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineArrowSize;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineEndShape;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineInfo;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.LineType;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponent.lineinfo.OutlineStyle;
import kr.dogfoot.hwplib.object.bodytext.control.gso.shapecomponenteach.ShapeComponentPicture;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.charshape.CharPositionShapeIdPair;
import kr.dogfoot.hwplib.object.docinfo.BinData;
import kr.dogfoot.hwplib.object.docinfo.bindata.BinDataCompress;
import kr.dogfoot.hwplib.object.docinfo.bindata.BinDataState;
import kr.dogfoot.hwplib.object.docinfo.bindata.BinDataType;
import kr.dogfoot.hwplib.object.docinfo.borderfill.fillinfo.PictureEffect;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DirectoryEntry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.Entry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.POIFSFileSystem;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.writer.HWPWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 관리자가 내려받는 안전·보건 제안서 HWP 양식의 일반 텍스트 토큰을 신고 데이터로 치환한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateHwpReportGenerator implements HwpReportGenerator {
    private static final String TEMPLATE = "templates/dasigochim-report-template.hwp";
    private static final String HWP_MIME_TYPE = "application/x-hwp";
    private static final Pattern IMPROVEMENT_MARKER = Pattern.compile("\\n\\s*개선\\s*제안\\s*사항\\s*:\\s*");
    private static final int HWP_UNIT_PER_MM = 7200;
    private static final int MAX_PHOTO_HEIGHT = 45 * HWP_UNIT_PER_MM;

    private final FileStorageService storageService;
    private final ReportPhotoRepository photoRepository;

    @Override
    public String generate(Report report) {
        try (InputStream input = new ClassPathResource(TEMPLATE).getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            HWPFile hwp = HWPReader.fromInputStream(input);
            replaceTokens(hwp, tokenValues(report), photoRepository.findAllByReportIdOrderByDisplayOrder(report.getId()));
            HWPWriter.toStream(hwp, output);
            String trackingId = report.getTrackingId() == null ? "draft-" + report.getId() : report.getTrackingId();
            return storageService.store(removeEmptyScriptsDirectory(output.toByteArray()), "안전보건제안서-" + trackingId + ".hwp", HWP_MIME_TYPE).url();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("HWP 신고서 생성 실패: reportId={}", report.getId(), exception);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "HWP 신고서 생성에 실패했습니다.");
        }
    }

    /**
     * hwplib가 HWP를 다시 쓸 때 비어 있는 {@code Scripts} 저장소를 만들 수 있다.
     * 한컴은 Scripts 저장소가 존재하면 DefaultJScript 스트림도 요구하므로, 비어 있을 때만 제거한다.
     */
    private byte[] removeEmptyScriptsDirectory(byte[] rawHwp) throws Exception {
        POIFSFileSystem fileSystem = new POIFSFileSystem(new ByteArrayInputStream(rawHwp));
        DirectoryEntry root = fileSystem.getRoot();
        if (root.hasEntry("Scripts")) {
            Entry scripts = root.getEntry("Scripts");
            if (scripts instanceof DirectoryEntry directory && directory.isEmpty()) scripts.delete();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        fileSystem.writeFilesystem(output);
        return output.toByteArray();
    }

    private Map<String, String> tokenValues(Report report) {
        DescriptionParts description = splitDescription(report.getDescription());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("{{학과}}", valueOrDash(report.getReporter().getMajor()));
        values.put("{{이름}}", valueOrDash(report.getReporter().getName()));
        values.put("{{연락처}}", valueOrDash(report.getReporter().getPhone()));
        values.put("{{위험장소}}", riskLocation(report));
        values.put("{{내용}}", valueOrDash(description.content()));
        values.put("{{제안사항}}", valueOrDash(description.improvement()));
        values.put("{{사진}}", "첨부 사진 없음");
        return values;
    }

    private void replaceTokens(HWPFile hwp, Map<String, String> values, List<ReportPhoto> photos) throws Exception {
        for (Section section : hwp.getBodyText().getSectionList()) {
            for (Paragraph paragraph : section.getParagraphs()) {
                replaceInParagraph(paragraph, values);
                if (paragraph.getControlList() == null) continue;
                for (Control control : paragraph.getControlList()) {
                    if (control instanceof ControlTable table) replaceInTable(hwp, table, values, photos);
                }
            }
        }
    }

    private void replaceInTable(HWPFile hwp, ControlTable table, Map<String, String> values, List<ReportPhoto> photos) throws Exception {
        for (Row row : table.getRowList()) {
            for (Cell cell : row.getCellList()) {
                for (Paragraph paragraph : cell.getParagraphList().getParagraphs()) {
                    if (paragraph.getText() != null && paragraph.getNormalString().contains("{{사진}}")) {
                        replacePhotoCell(hwp, cell, paragraph, photos);
                    } else {
                        replaceInParagraph(paragraph, values);
                    }
                }
            }
        }
    }

    private void replacePhotoCell(HWPFile hwp, Cell cell, Paragraph paragraph, List<ReportPhoto> photos) throws Exception {
        if (photos.isEmpty()) {
            replaceParagraphText(paragraph, "첨부 사진 없음");
            return;
        }
        ReportPhoto photo = photos.getFirst();
        FileStorageService.LoadedFile loaded = storageService.load(photo.getUrl());
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(loaded.bytes()));
        if (source == null) {
            replaceParagraphText(paragraph, "사진 형식을 읽을 수 없습니다.");
            return;
        }
        replaceParagraphText(paragraph, "");
        ImageSize size = photoBox(cell);
        byte[] pictureBytes = renderContainImage(applyExifOrientation(source, loaded.bytes()), size);
        int streamIndex = hwp.getBinData().getEmbeddedBinaryDataList().size() + 1;
        String extension = "png";
        hwp.getBinData().addNewEmbeddedBinaryData(String.format("BIN%04X.%s", streamIndex, extension), pictureBytes,
                BinDataCompress.ByStorageDefault);
        BinData binData = hwp.getDocInfo().addNewBinData();
        binData.getProperty().setType(BinDataType.Embedding);
        binData.getProperty().setCompress(BinDataCompress.ByStorageDefault);
        binData.getProperty().setState(BinDataState.NotAccess);
        binData.setBinDataID(streamIndex);
        binData.setExtensionForEmbedding(extension);
        hwp.getDocInfo().getIDMappings().setBinDataCount(streamIndex);
        addPicture(paragraph, streamIndex, size);
    }

    private void addPicture(Paragraph paragraph, int binDataId, ImageSize size) {
        paragraph.getText().addExtendCharForGSO();
        ControlPicture picture = (ControlPicture) paragraph.addNewGsoControl(GsoControlType.Picture);
        CtrlHeaderGso header = picture.getHeader();
        header.getProperty().setLikeWord(true);
        header.getProperty().setApplyLineSpace(false);
        header.getProperty().setVertRelTo(VertRelTo.Para);
        header.getProperty().setVertRelativeArrange(RelativeArrange.TopOrLeft);
        header.getProperty().setHorzRelTo(HorzRelTo.Para);
        header.getProperty().setHorzRelativeArrange(RelativeArrange.TopOrLeft);
        header.getProperty().setVertRelToParaLimit(true);
        header.getProperty().setAllowOverlap(false);
        header.getProperty().setWidthCriterion(WidthCriterion.Absolute);
        header.getProperty().setHeightCriterion(HeightCriterion.Absolute);
        header.getProperty().setProtectSize(false);
        header.getProperty().setTextFlowMethod(TextFlowMethod.TakePlace);
        header.getProperty().setTextHorzArrange(TextHorzArrange.LeftOnly);
        header.getProperty().setObjectNumberSort(ObjectNumberSort.Figure);
        header.setxOffset(0);
        header.setyOffset(0);
        header.setWidth(size.width());
        header.setHeight(size.height());
        header.setzOrder(0);
        header.setOutterMarginLeft(0);
        header.setOutterMarginRight(0);
        header.setOutterMarginTop(0);
        header.setOutterMarginBottom(0);
        header.setInstanceId(0x5bb840e1L + binDataId);
        header.setPreventPageDivide(false);

        ShapeComponentNormal normal = (ShapeComponentNormal) picture.getShapeComponent();
        normal.setOffsetX(0);
        normal.setOffsetY(0);
        normal.setGroupingCount(0);
        normal.setLocalFileVersion(1);
        normal.setWidthAtCreate(size.width());
        normal.setHeightAtCreate(size.height());
        normal.setWidthAtCurrent(size.width());
        normal.setHeightAtCurrent(size.height());
        normal.setRotateAngle(0);
        normal.setRotateXCenter(size.width() / 2);
        normal.setRotateYCenter(size.height() / 2);
        normal.createLineInfo();
        LineInfo lineInfo = normal.getLineInfo();
        lineInfo.getProperty().setLineEndShape(LineEndShape.Flat);
        lineInfo.getProperty().setStartArrowShape(LineArrowShape.None);
        lineInfo.getProperty().setStartArrowSize(LineArrowSize.MiddleMiddle);
        lineInfo.getProperty().setEndArrowShape(LineArrowShape.None);
        lineInfo.getProperty().setEndArrowSize(LineArrowSize.MiddleMiddle);
        lineInfo.getProperty().setFillStartArrow(true);
        lineInfo.getProperty().setFillEndArrow(true);
        lineInfo.getProperty().setLineType(LineType.None);
        lineInfo.setOutlineStyle(OutlineStyle.Normal);
        lineInfo.setThickness(0);
        lineInfo.getColor().setValue(0);
        normal.setMatrixsNormal();

        ShapeComponentPicture component = picture.getShapeComponentPicture();
        component.getBorderColor().setValue(0);
        component.setBorderThickness(0);
        component.getBorderProperty().setLineEndShape(LineEndShape.Flat);
        component.getBorderProperty().setStartArrowShape(LineArrowShape.None);
        component.getBorderProperty().setStartArrowSize(LineArrowSize.MiddleMiddle);
        component.getBorderProperty().setEndArrowShape(LineArrowShape.None);
        component.getBorderProperty().setEndArrowSize(LineArrowSize.MiddleMiddle);
        component.getBorderProperty().setLineType(LineType.None);
        component.getLeftTop().setX(0);
        component.getLeftTop().setY(size.height());
        component.getRightTop().setX(size.width());
        component.getRightTop().setY(size.height());
        component.getLeftBottom().setX(0);
        component.getLeftBottom().setY(0);
        component.getRightBottom().setX(size.width());
        component.getRightBottom().setY(0);
        component.getPictureInfo().setBinItemID(binDataId);
        component.getPictureInfo().setBrightness((byte) 0);
        component.getPictureInfo().setContrast((byte) 0);
        component.getPictureInfo().setEffect(PictureEffect.RealPicture);
        component.setImageWidth(size.width());
        component.setImageHeight(size.height());
        component.setInstanceId(0x5bb810e1L + binDataId);
        component.setLeftAfterCutting(0);
        component.setTopAfterCutting(0);
        component.setRightAfterCutting(0);
        component.setBottomAfterCutting(0);
    }

    private ImageSize photoBox(Cell cell) {
        int maxWidth = Math.max(1, (int) cell.getListHeader().getWidth() - cell.getListHeader().getLeftMargin() - cell.getListHeader().getRightMargin());
        int cellHeight = Math.max(1, (int) cell.getListHeader().getHeight() - cell.getListHeader().getTopMargin() - cell.getListHeader().getBottomMargin());
        int maxHeight = Math.min(cellHeight, MAX_PHOTO_HEIGHT);
        return new ImageSize(maxWidth, maxHeight);
    }

    /**
     * HWP가 그림 객체의 영역을 늘려 표시하더라도 사진 본체가 찌그러지지 않도록,
     * 사진을 대상 셀과 같은 비율의 PNG 캔버스 가운데에 비율 유지(contain)로 그린다.
     */
    private byte[] renderContainImage(BufferedImage source, ImageSize target) throws Exception {
        int canvasHeight = 1200;
        int canvasWidth = Math.max(1, (int) Math.round((double) target.width() / target.height() * canvasHeight));
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double scale = Math.min((double) canvasWidth / source.getWidth(), (double) canvasHeight / source.getHeight());
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            graphics.drawImage(source, (canvasWidth - width) / 2, (canvasHeight - height) / 2, width, height, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", output);
        return output.toByteArray();
    }

    /** 휴대폰 JPEG의 EXIF 방향을 브라우저와 같은 방향으로 실제 픽셀에 반영한다. */
    private BufferedImage applyExifOrientation(BufferedImage source, byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            int orientation = exif == null ? 1 : exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            return switch (orientation) {
                case 3 -> rotate(source, Math.PI);
                case 6 -> rotate(source, Math.PI / 2);
                case 8 -> rotate(source, -Math.PI / 2);
                default -> source;
            };
        } catch (Exception ignored) {
            return source;
        }
    }

    private BufferedImage rotate(BufferedImage source, double radians) {
        boolean sideways = Math.abs(radians) == Math.PI / 2;
        int width = sideways ? source.getHeight() : source.getWidth();
        int height = sideways ? source.getWidth() : source.getHeight();
        BufferedImage rotated = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rotated.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            if (radians == Math.PI / 2) graphics.translate(width, 0);
            else if (radians == -Math.PI / 2) graphics.translate(0, height);
            else graphics.translate(width, height);
            graphics.rotate(radians);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rotated;
    }

    private void replaceInParagraph(Paragraph paragraph, Map<String, String> values) throws Exception {
        if (paragraph.getText() == null) return;
        String text = paragraph.getNormalString();
        String replaced = text;
        for (Map.Entry<String, String> entry : values.entrySet()) replaced = replaced.replace(entry.getKey(), entry.getValue());
        if (text.equals(replaced)) return;

        replaceParagraphText(paragraph, replaced);
    }

    private void replaceParagraphText(Paragraph paragraph, String text) throws Exception {
        long charShapeId = 0;
        if (paragraph.getCharShape() != null && !paragraph.getCharShape().getPositonShapeIdPairList().isEmpty()) charShapeId = paragraph.getCharShape().getPositonShapeIdPairList().getFirst().getShapeId();
        paragraph.deleteText();
        paragraph.createText();
        paragraph.getText().addString(text);
        paragraph.getHeader().setCharacterCount(paragraph.getText().getCharSize());
        if (paragraph.getCharShape() != null) {
            paragraph.getCharShape().getPositonShapeIdPairList().clear();
            paragraph.getCharShape().getPositonShapeIdPairList().add(new CharPositionShapeIdPair(0, charShapeId));
            paragraph.getHeader().setCharShapeCount(1);
        }
        paragraph.deleteLineSeg();
    }

    private DescriptionParts splitDescription(String description) {
        String source = description == null ? "" : description.trim();
        Matcher matcher = IMPROVEMENT_MARKER.matcher(source);
        if (!matcher.find()) return new DescriptionParts(source, "");
        return new DescriptionParts(source.substring(0, matcher.start()).trim(), source.substring(matcher.end()).trim());
    }

    private String riskLocation(Report report) {
        String buildingName = report.getBuilding() != null ? report.getBuilding().getName() : report.getBuildingNameSnapshot();
        String detail = report.getLocationDescription();
        if (detail == null || detail.isBlank()) return valueOrDash(buildingName);
        if (buildingName == null || buildingName.isBlank() || detail.startsWith(buildingName)) return detail;
        return buildingName + " " + detail;
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private record DescriptionParts(String content, String improvement) {}
    private record ImageSize(int width, int height) {}
}
