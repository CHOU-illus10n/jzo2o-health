package com.jzo2o.health.controller.admin;

import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.health.service.IReservationSettingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

/**
 * 预约设置操作
 *
 * @author itcast
 */
@Slf4j
@RestController("adminReservationBatchSettingController")
@RequestMapping("/admin/reservation-setting")
@Api(tags = "管理端 - 批量预约设置相关接口")
public class ReservationBatchSettingController {

    @Resource
    private IReservationSettingService reservationSettingService;

    @PostMapping("/upload")
    @ApiOperation("批量上传预约设置")
    public void upload(@RequestPart("file") MultipartFile file) {
        try{
            reservationSettingService.upload(file);
        }catch (Exception e){
            throw  new BadRequestException("上传文件失败，请重新上传");
        }
    }

}
