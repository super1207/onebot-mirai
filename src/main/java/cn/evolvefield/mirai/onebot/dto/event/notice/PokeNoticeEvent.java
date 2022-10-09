package cn.evolvefield.mirai.onebot.dto.event.notice;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Created on 2022/7/8.
 *
 * @author cnlimiter
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class PokeNoticeEvent extends NoticeEvent {

    @JSONField(name = "sub_type")
    private String subType;

    @JSONField(name = "self_id")
    private long selfId;

    @JSONField(name = "sender_id")
    private long senderId;

    @JSONField(name = "user_id")
    private long userId;

    @JSONField(name = "target_id")
    private long targetId;

    @JSONField(name = "group_id")
    private long groupId;

    @JSONField(name = "time")
    private long time;

}
