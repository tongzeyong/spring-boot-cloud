package cn.com.tzy.springbootstarterfreeswitch.redis.subscribe.sip.message;


import cn.com.tzy.springbootcomm.common.vo.RespCode;
import cn.com.tzy.springbootstarterfreeswitch.client.sip.SipServer;
import cn.com.tzy.springbootstarterfreeswitch.client.sip.utils.SipLogUtils;
import cn.com.tzy.springbootstarterfreeswitch.common.sip.SipConstant;
import cn.com.tzy.springbootstarterfreeswitch.model.fs.AgentVoInfo;
import cn.com.tzy.springbootstarterfreeswitch.redis.RedisService;
import cn.com.tzy.springbootstarterfreeswitch.vo.result.RestResultEvent;
import cn.com.tzy.springbootstarterfreeswitch.vo.sip.Address;
import cn.com.tzy.springbootstarterfreeswitch.vo.sip.EventResult;
import cn.com.tzy.springbootstarterfreeswitch.vo.sip.MessageTypeVo;
import cn.com.tzy.springbootstarterredis.pool.AbstractMessageListener;
import cn.com.tzy.springbootstarterredis.utils.RedisUtils;
import cn.hutool.core.codec.Base64;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.SIPClientTransactionImpl;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import javax.annotation.Resource;
import javax.sip.SipException;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.Arrays;
import java.util.List;

@Log4j2
@Component
public class SipMessageHandle extends AbstractMessageListener {

    @Resource
    private NacosDiscoveryProperties nacosDiscoveryProperties;


    public SipMessageHandle() {
        super(SipConstant.VIDEO_SEND_SIP_MESSAGE);
    }

    @Override
    public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
        try {
            SipServer sipServer = SpringUtil.getBean(SipServer.class);
            Object body = RedisUtils.redisTemplate.getValueSerializer().deserialize(message.getBody());
            Object deserialize = SerializationUtils.deserialize(Base64.decode((String) body));
            MessageTypeVo vo = (MessageTypeVo) deserialize;
            if(MessageTypeVo.TypeEnum.SIP.getValue()== vo.getType()){
                if(!RedisService.getRegisterServerManager().isNotServerDevice(vo.getAgentKey())){
                    log.error("[SIP接收消息] [设备] 未获取注册地址 gbId : {}",vo.getAgentKey());
                    sendErrorMsg(sipServer, vo.getMessage(), String.format("未获取设备注册地址 国标编号 :%s",vo.getAgentKey()));
                    return;
                }
                Address address = RedisService.getRegisterServerManager().getDevice(vo.getAgentKey());
                if(address == null){
                    log.warn("[SIP接收消息] [设备] 在其他服务注册 gbId : {}",vo.getAgentKey());
                    return;
                }
                AgentVoInfo agentVoInfo = RedisService.getAgentInfoManager().get(vo.getAgentKey());
                if(agentVoInfo != null &&  nacosDiscoveryProperties.getIp().equals(address.getIp()) &&  nacosDiscoveryProperties.getPort() == address.getPort()){
                    String localIp = sipServer.getLocalIp(agentVoInfo.getFsHost());
                    handleMessage(localIp,vo.getMessage());
                }
            }else if(MessageTypeVo.TypeEnum.SOCKET.getValue()== (vo.getType())){
                if(!RedisService.getRegisterServerManager().isNotPlatformDevice(vo.getAgentKey())){
                    log.error("[SIP接收消息] [国标级联] 未获取注册地址 gbId : {}",vo.getAgentKey());
                    sendErrorMsg(sipServer, vo.getMessage(), String.format("未获取国标级联注册地址 国标编号 :%s",vo.getAgentKey()));
                    return;
                }
                Address address = RedisService.getRegisterServerManager().getPlatform(vo.getAgentKey());
                if(address == null){
                    log.warn("[SIP接收消息] [设备] 在其他服务注册 gbId : {}",vo.getAgentKey());
                    return;
                }
                AgentVoInfo agentVoInfo = RedisService.getAgentInfoManager().get(vo.getAgentKey());
                if(agentVoInfo != null &&  nacosDiscoveryProperties.getIp().equals(address.getIp()) &&  nacosDiscoveryProperties.getPort() == address.getPort()){
                    String localIp = sipServer.getLocalIp(agentVoInfo.getFsHost());
                    handleMessage(localIp,vo.getMessage());
                }
            }else {
                log.error("[SIP接收消息] 类型错误:{}", JSONUtil.toJsonStr(vo));
                sendErrorMsg(sipServer, vo.getMessage(), String.format("消息类型错误 :%s",JSONUtil.toJsonStr(vo)));
            }
        }catch (Exception e){
            log.error("[SIP接收消息] 发生错误:", e);
        }
    }
    /**
     * @param ip        发送端SIP IP
     * @param message   消息体
     * @throws SipException
     */
    public void handleMessage(String ip, Message message) throws SipException {
        SipServer sipServer = SpringUtil.getBean(SipServer.class);
        ViaHeader viaHeader = (ViaHeader) message.getHeader(ViaHeader.NAME);
        String transport = "UDP";
        if (viaHeader == null) {
            log.warn("[消息头缺失]： ViaHeader， 使用默认的UDP方式处理数据");
        } else {
            transport = viaHeader.getTransport();
        }
        //打印日志
        SipLogUtils.sendMessage(sipServer,message);
        if ("TCP".equals(transport)) {
            SipProviderImpl tcpSipProvider = sipServer.getTcpSipProvider(ip);
            if (tcpSipProvider == null) {
                log.error("[发送信息失败] 未找到tcp://{}的监听信息", ip);
                sendErrorMsg(sipServer, message, String.format("未找到tcp://%s的监听信息",ip));
                return;
            }
            sendSip(tcpSipProvider, message);
        } else if ("UDP".equals(transport)) {
            SipProviderImpl sipProvider = sipServer.getUdpSipProvider(ip);
            if (sipProvider == null) {
                log.error("[发送信息失败] 未找到udp://{}的监听信息", ip);
                sendErrorMsg(sipServer, message, String.format("未找到udp://%s的监听信息",ip));
                return;
            }
            sendSip(sipProvider, message);
        }
    }

    /**
     *  sip事务实现类 SIPClientTransactionImpl
     * 除过ACK以及BYE请求后 其他发生消息类型会开启事务sip
     * 开启事务后 如回复状态码为 300 <= statusCode && statusCode <= 699 后会自动回复ACK消息 代码在 SIPClientTransactionImpl 的903行
     */
    private static void sendSip(SipProviderImpl sipProvider, Message message) throws SipException {
        if (message instanceof Request) {
            List<String> methodList = Arrays.asList(Request.ACK,Request.BYE);
            if(methodList.contains(((SIPRequest)message).getMethod())){
                sipProvider.sendRequest((Request)message);
            }else {
                //开启sip事务
                SIPClientTransactionImpl newClientTransaction = (SIPClientTransactionImpl)sipProvider.getNewClientTransaction((Request) message);
                newClientTransaction.setRetransmitTimer(1000);//重试 与 超时 时间间隔 1000毫秒
                newClientTransaction.setTimerD(32000);//32秒未回应超时
                newClientTransaction.disableRetransmissionTimer();//取消重试机制 //目前机制 为 发送消息后 32秒未收到回应则触发 processTimeout 超时机制
                newClientTransaction.setTimerT2(-1000);//取消重试机制
                newClientTransaction.sendRequest();
            }
        } else if (message instanceof Response) {
            sipProvider.sendResponse((Response) message);
        }
    }

    private void sendErrorMsg(SipServer sipServer,Message message,String error){
        if(message == null){
            log.error("[发送信息失败] 发送错误消息时,未获取消息主体");
            return;
        }
        CallIdHeader callIdHeader = (CallIdHeader) message.getHeader(CallIdHeader.NAME);
        SipSubscribeHandle sipSubscribeHandle = sipServer.getSipSubscribeManager();
        List<SipSubscribeEvent> errorSubscribe = sipSubscribeHandle.getErrorSubscribe(callIdHeader.getCallId());
        if(errorSubscribe ==null || errorSubscribe.isEmpty()){
            return;
        }
        for (SipSubscribeEvent sipSubscribeEvent : errorSubscribe) {
            sipSubscribeEvent.response(new EventResult<RestResultEvent>(new RestResultEvent(RespCode.CODE_2.getValue(),error)));
        }
        sipSubscribeHandle.removeAllSubscribe(callIdHeader.getCallId());
    }

}