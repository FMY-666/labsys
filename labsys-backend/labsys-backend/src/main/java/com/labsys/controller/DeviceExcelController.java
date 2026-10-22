package com.labsys.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsys.common.Result;
import com.labsys.entity.Device;
import com.labsys.entity.DeviceDict;
import com.labsys.mapper.DeviceDictMapper;
import com.labsys.mapper.DeviceMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备台账的 Excel 导入导出。
 *
 * 导入是"逐行校验、部分成功"：一行不合法只跳过那一行并回报原因，不会整批回滚。
 * 几十台设备录到第 30 行因为一个编号重复全废掉，比只失败一行难受得多。
 */
@RestController
@RequestMapping("/api")
public class DeviceExcelController {

    private static final String[] HEADERS = {"设备编号*", "名称*", "分类", "状态", "存放位置", "品牌", "型号"};

    /** 状态允许写中文也允许写英文码 —— 导出的表格给中国人看，导回来时两种都认 */
    private static final Map<String, String> STATUS_IN = new HashMap<>();
    private static final Map<String, String> STATUS_OUT = new LinkedHashMap<>();

    static {
        STATUS_IN.put("在库", "AVAILABLE");
        STATUS_IN.put("可借用", "AVAILABLE");
        STATUS_IN.put("AVAILABLE", "AVAILABLE");
        STATUS_IN.put("借出", "BORROWED");
        STATUS_IN.put("已借出", "BORROWED");
        STATUS_IN.put("BORROWED", "BORROWED");
        STATUS_IN.put("维修中", "REPAIRING");
        STATUS_IN.put("REPAIRING", "REPAIRING");
        STATUS_IN.put("已报废", "SCRAPPED");
        STATUS_IN.put("SCRAPPED", "SCRAPPED");

        STATUS_OUT.put("AVAILABLE", "在库");
        STATUS_OUT.put("BORROWED", "借出");
        STATUS_OUT.put("REPAIRING", "维修中");
        STATUS_OUT.put("SCRAPPED", "已报废");
    }

    private final DeviceMapper deviceMapper;
    private final DeviceDictMapper dictMapper;

    public DeviceExcelController(DeviceMapper deviceMapper, DeviceDictMapper dictMapper) {
        this.deviceMapper = deviceMapper;
        this.dictMapper = dictMapper;
    }

    @GetMapping("/devices/export")
    public void export(HttpServletResponse response) throws IOException {
        List<Device> list = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().orderByAsc(Device::getId));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("设备台账");

            CellStyle headStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);

            Row head = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }

            int r = 1;
            for (Device d : list) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(nullSafe(d.getDeviceNo()));
                row.createCell(1).setCellValue(nullSafe(d.getName()));
                row.createCell(2).setCellValue(nullSafe(d.getCategory()));
                row.createCell(3).setCellValue(STATUS_OUT.getOrDefault(d.getStatus(), d.getStatus()));
                row.createCell(4).setCellValue(nullSafe(d.getLocation()));
                row.createCell(5).setCellValue(nullSafe(d.getBrand()));
                row.createCell(6).setCellValue(nullSafe(d.getModel()));
            }

            String fileName = "设备台账.xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            wb.write(response.getOutputStream());
        }
    }

    @PostMapping("/devices/import")
    public Result importDevices(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return new Result(400, "请选择要导入的 Excel 文件", null);
        }

        List<Map<String, Object>> failed = new ArrayList<>();
        int inserted = 0;

        // 文件内部也要查重：同一个编号在表里出现两次，第二次不该插进去
        Set<String> seen = new HashSet<>();
        deviceMapper.selectList(new LambdaQueryWrapper<Device>().select(Device::getDeviceNo))
                .forEach(d -> seen.add(d.getDeviceNo()));

        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                // 行号按 Excel 的习惯从 1 开始，第一行是表头，所以数据行是 i+1
                int rowNo = i + 1;

                String deviceNo = cell(row, 0);
                String name = cell(row, 1);
                String category = cell(row, 2);
                String statusText = cell(row, 3);
                String location = cell(row, 4);
                String brand = cell(row, 5);
                String model = cell(row, 6);

                String error = validate(deviceNo, name, category, location, brand, model);
                if (error == null && seen.contains(deviceNo)) {
                    error = "设备编号「" + deviceNo + "」重复";
                }
                String status = "AVAILABLE";
                if (statusText != null && !statusText.isEmpty()) {
                    status = STATUS_IN.get(statusText.trim());
                    if (status == null) {
                        error = "状态「" + statusText + "」不认识，只能填 在库/借出/维修中/已报废";
                    }
                }

                if (error != null) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("row", rowNo);
                    f.put("deviceNo", deviceNo);
                    f.put("reason", error);
                    failed.add(f);
                    continue;
                }

                Device d = new Device();
                d.setDeviceNo(deviceNo);
                d.setName(name);
                d.setCategory(category);
                d.setStatus(status);
                d.setLocation(location);
                d.setBrand(brand);
                d.setModel(model);
                deviceMapper.insert(d);
                seen.add(deviceNo);
                inserted++;

                rememberDict(DeviceDict.CATEGORY, category);
                rememberDict(DeviceDict.LOCATION, location);
            }
        } catch (Exception e) {
            return new Result(400, "文件解析失败，请确认是 .xlsx 格式：" + e.getMessage(), null);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inserted", inserted);
        data.put("failed", failed);
        return new Result(200, "导入完成", data);
    }

    /** 返回 null 表示这一行合法 */
    private String validate(String deviceNo, String name, String category,
                            String location, String brand, String model) {
        if (deviceNo == null || deviceNo.isEmpty()) return "设备编号不能为空";
        if (deviceNo.length() > 50) return "设备编号超过 50 字";
        if (name == null || name.isEmpty()) return "名称不能为空";
        if (name.length() > 100) return "名称超过 100 字";
        if (category != null && category.length() > 50) return "分类超过 50 字";
        if (location != null && location.length() > 100) return "存放位置超过 100 字";
        if (brand != null && brand.length() > 50) return "品牌超过 50 字";
        if (model != null && model.length() > 50) return "型号超过 50 字";
        return null;
    }

    private boolean isBlankRow(Row row) {
        for (int c = 0; c < HEADERS.length; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !cellString(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cell(Row row, int index) {
        Cell cell = row.getCell(index);
        return cell == null ? null : cellString(cell).trim();
    }

    /** 数字型编号（Excel 会把 NET-01 之类当文本，但纯数字编号会被存成数值）单独处理 */
    private String cellString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private void rememberDict(String type, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        Long exists = dictMapper.selectCount(new LambdaQueryWrapper<DeviceDict>()
                .eq(DeviceDict::getType, type)
                .eq(DeviceDict::getValue, value));
        if (exists != null && exists > 0) {
            return;
        }
        try {
            DeviceDict d = new DeviceDict();
            d.setType(type);
            d.setValue(value);
            dictMapper.insert(d);
        } catch (Exception ignore) {
            // 唯一索引冲突，别人刚好插了
        }
    }
}
