package com.jzo2o.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.health.model.domain.ReservationSetting;
import com.jzo2o.health.model.dto.request.ReservationSettingUpsertReqDTO;
import com.jzo2o.health.model.dto.response.ReservationSettingResDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * @author zwy
 * @version 1.0
 * @description: TODO
 * @date 2024/5/22 14:24
 */
public interface IReservationSettingService extends IService<ReservationSetting> {

    /**
     * 获取预约设置
     */
    List<ReservationSettingResDTO> getByMonth(String date);

    void editNumberByDate(ReservationSettingUpsertReqDTO reservationSettingUpsertReqDTO);

    void upload(MultipartFile file) throws IOException;

    List<String> getReservationDateByMonth(String month);

    Integer getNumberByDate(LocalDate reservationDate);

    /**
     * 预约后更新数据
     */
    void updateReservation(LocalDate orderDate);
}
