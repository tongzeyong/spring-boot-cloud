package cn.com.tzy.springbootstarterfreeswitch.vo.sip;

import cn.com.tzy.springbootstarterfreeswitch.enums.sip.InviteSessionStatus;
import cn.com.tzy.springbootstarterfreeswitch.enums.sip.VideoStreamType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记录每次发送invite消息的状态
 */
@Data
@NoArgsConstructor
public class InviteInfo {

    /**
     * 操作用户
     */
    private Long userId;
    private String agentKey;
    private String receiveIp;
    /**
     * 数据流传输模式 0.UDP:udp传输 2.TCP-ACTIVE：tcp主动模式 2.TCP-PASSIVE：tcp被动模式
     */
    private Integer streamMode;
    private VideoStreamType type;
    private InviteSessionStatus status;
    private StreamInfo streamInfo;
    private SSRCInfo audioSsrcInfo;
    private SSRCInfo videoSsrcInfo;
    public InviteInfo(Long userId, String agentKey, SSRCInfo videoSsrcInfo,SSRCInfo audioSsrcInfo, String receiveIp, Integer streamMode, VideoStreamType type, InviteSessionStatus status){
        this.userId = userId;
        this.agentKey = agentKey;
        this.videoSsrcInfo = videoSsrcInfo;
        this.audioSsrcInfo = audioSsrcInfo;
        this.receiveIp = receiveIp;
        this.streamMode = streamMode;
        this.type = type;
        this.status = status;
    }
}