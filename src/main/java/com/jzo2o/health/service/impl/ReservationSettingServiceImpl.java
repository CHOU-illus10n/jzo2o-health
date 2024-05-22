package com.jzo2o.health.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.health.mapper.ReservationSettingMapper;
import com.jzo2o.health.model.domain.ReservationSetting;
import com.jzo2o.health.model.dto.request.ReservationSettingUpsertReqDTO;
import com.jzo2o.health.model.dto.response.ReservationSettingResDTO;
import com.jzo2o.health.service.IReservationSettingService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zwy
 * @version 1.0
 * @description: TODO
 * @date 2024/5/22 14:25
 */
@Service
public class ReservationSettingServiceImpl extends ServiceImpl<ReservationSettingMapper, ReservationSetting> implements IReservationSettingService {


    @Override
    public List<ReservationSettingResDTO> getByMonth(String date) {
        //使用相似来查询数据库
        List<ReservationSetting> list = lambdaQuery().like(ReservationSetting::getOrderDate, date).list();
        List<ReservationSettingResDTO> res = new ArrayList<>();
        for(ReservationSetting reservationSetting : list){
            //将数据库中的数据封装成DTO返回 reservations和number都可以转换，但是date需要改为string
            ReservationSettingResDTO reservationSettingResDTO = BeanUtil.toBean(reservationSetting, ReservationSettingResDTO.class);
            LocalDate orderDate = reservationSetting.getOrderDate();
            // 定义日期格式
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            // 将LocalDate格式的数据转换为指定的字符串格式
            String formattedDate = orderDate.format(formatter);
            reservationSettingResDTO.setDate(formattedDate);
            res.add(reservationSettingResDTO);
        }
        return res;
    }

    /**
     * 修改预约设置
     * @param reservationSettingUpsertReqDTO
     */
    @Override
    public void editNumberByDate(ReservationSettingUpsertReqDTO reservationSettingUpsertReqDTO) {
        LocalDate orderDate = reservationSettingUpsertReqDTO.getOrderDate();
        Integer number = reservationSettingUpsertReqDTO.getNumber();
        saveOrUpdateReservationSetting(orderDate,number);
    }

    @Override
    public void upload(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream))
        {
            // 2.获取Excel文件的第一个Sheet页（只有一个）
            Sheet sheet = workbook.getSheetAt(0);
            // 3.按行遍历
            for (Row row : sheet) {
                // 3.1跳过表头
                if (row.getRowNum() == 0) {
                    continue;
                }
                // 3.2每次遍历一行 取出 日期 和 预约数量
                Cell dateCell = row.getCell(0);
                Cell numberCell = row.getCell(1);
                // 检查有没有单元格为空 只要有一个为空则说明填写有误 跳过
                if (dateCell == null || numberCell == null) {
                    continue;
                }
                String dateStr = dateCell.getStringCellValue();
                //String转LocalDate
                LocalDate orderDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                int number = (int)numberCell.getNumericCellValue();
                if(number <0 || number>=1000){
                    throw new BadRequestException("预约数量不能为负数或大于1000");
                }
                saveOrUpdateReservationSetting(orderDate,number);
            }

        }
    }

    @Override
    public List<String> getReservationDateByMonth(String month) {
        //按月份遍历数据库查询
        List<ReservationSetting> list = lambdaQuery().like(ReservationSetting::getOrderDate, month)
                .gt(ReservationSetting::getNumber, 0)
                .list();
        List<String> dateList = new ArrayList<>();
        for (ReservationSetting reservationSetting : list) {
            LocalDate orderDate = reservationSetting.getOrderDate();
            //转换为String
            String formattedDate = orderDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            dateList.add(formattedDate);
        }
        return dateList;
    }


    private void saveOrUpdateReservationSetting(LocalDate orderDate, Integer number) {
        //如果没有则新增，有的话更新
        ReservationSetting reservationSetting = new ReservationSetting();
        reservationSetting.setOrderDate(orderDate);
        reservationSetting.setNumber(number);
        UpdateWrapper<ReservationSetting> updateWrapper = new UpdateWrapper<ReservationSetting>().eq("order_date", orderDate);
        saveOrUpdate(reservationSetting,updateWrapper);
    }

    @Override
    public Integer getNumberByDate(LocalDate reservationDate) {
        ReservationSetting reservationSetting = lambdaQuery().eq(ReservationSetting::getOrderDate, reservationDate).one();
        if (ObjectUtils.isEmpty(reservationSetting)) {
            throw new CommonException("当前日期没有设置预约人数");
        }
        return reservationSetting.getNumber();
    }

    @Override
    public void updateReservation(LocalDate orderDate) {
        Integer number = getNumberByDate(orderDate);
        if(number <= 0){
            throw new BadRequestException("预约人数已满");
        }
        lambdaUpdate().eq(ReservationSetting::getOrderDate, orderDate)
                .setSql("number = number - 1")
                .setSql("reservations = reservations + 1")
                .update();
    }


}
