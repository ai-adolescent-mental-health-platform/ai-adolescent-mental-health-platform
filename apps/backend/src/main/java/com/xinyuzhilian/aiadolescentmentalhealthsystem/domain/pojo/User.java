package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDate;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * <p>
 * 用户信息表
 * </p>
 *
 * @author 魏辰
 * @since 2026-01-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user")
@ApiModel(value="User对象", description="用户信息表")
@AllArgsConstructor
@NoArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键递增，不为空")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "用户名（不为空）--登录（唯一索引）")
    @TableField("username")
    private String username;

    @ApiModelProperty(value = "密码（不为空，不含SQL注入特殊字符，加密存储）")
    @TableField("password")
    @JsonIgnore
    private String password;

    @ApiModelProperty(value = "角色（0-游客，1-普通用户，2-医生，3-医生管理员，4-超级管理员，默认1）")
    @TableField("role")
    private Integer role;

    @ApiModelProperty(value = "邮箱（通过邮箱找回密码）")
    @TableField("email")
    private String email;

    @ApiModelProperty(value = "手机验证码登录/忘记密码找回密码")
    @TableField("phone")
    private String phone;

    @ApiModelProperty(value = "个性签名")
    @TableField("signature")
    private String signature;

    @ApiModelProperty(value = "昵称（默认占位，后端生成“用户+7位随机值”）")
    @TableField("nickname")
    private String nickname;

    @ApiModelProperty(value = "出生日期")
    @TableField("birthday")
    private LocalDate birthday;

    @ApiModelProperty(value = "性别（1-男，2-女）")
    @TableField("sex")
    private Integer sex;

    @ApiModelProperty(value = "头像路径（默认未知的头像）")
    @TableField("head_path")
    private String headPath;

    @ApiModelProperty(value = "创建时间（自动填充当前时间）")
    @TableField("create_time")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间（更新时自动刷新）")
    @TableField("update_time")
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "逻辑删除标记（0-未删除，1-已删除，默认0）")
    @TableField("deleted")
    private Boolean deleted;

    @ApiModelProperty(value = "账号状态（0-禁用（不可用），1-正常（可用），2-冻结（待解锁），默认1）")
    @TableField("status")
    private Integer status;

    @ApiModelProperty(value = "邮箱是否已验证(0-否,1-是)")
    @TableField("email_verified")
    private Integer emailVerified;

    @ApiModelProperty(value = "当月邮箱修改次数")
    @TableField("email_change_count")
    private Integer emailChangeCount;

    @ApiModelProperty(value = "最近修改邮箱的日期")
    @TableField("email_change_date")
    private LocalDate emailChangeDate;

    @ApiModelProperty(value = "是否为心理咨询师(0-否,1-是)")
    @TableField("is_psychologist")
    private Integer isPsychologist;

    @ApiModelProperty(value = "会员类型（0-非会员，1-VIP，2-SVIP）")
    @TableField("member_type")
    private Integer memberType;

    @ApiModelProperty(value = "会员过期时间")
    @TableField("member_expire_date")
    private LocalDateTime memberExpireDate;

}