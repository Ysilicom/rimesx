package org.scholay.rimes.android;

import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Local protocol fixture: no socket, URL, API key, files or model inference. */
final class MockOpenAiTransport {
    static final String PATH="/v1/chat/completions";
    interface Receiver { void onBytes(byte[] bytes,int offset,int length) throws OpenAiChatCodec.Failure; }
    interface Transport {
        void stream(byte[] request,PluginCancellation cancellation,Receiver receiver) throws OpenAiChatCodec.Failure;
    }
    static final Transport LOCAL=MockOpenAiTransport::stream;
    private MockOpenAiTransport() {}

    static void stream(byte[] request,PluginCancellation cancellation,Receiver receiver) throws OpenAiChatCodec.Failure {
        cancellation.check();
        OpenAiChatCodec.Request parsed=OpenAiChatCodec.readRequest(request);
        String reply=reply(parsed);
        emit(frame("",null,true),cancellation,receiver);
        int points=reply.codePointCount(0,reply.length()),chunkPoints=Math.max(1,(points+9)/10);
        for(int start=0;start<reply.length();) {
            cancellation.check();
            int end=reply.offsetByCodePoints(start,Math.min(chunkPoints,reply.codePointCount(start,reply.length())));
            emit(frame(reply.substring(start,end),null,false),cancellation,receiver);
            start=end;
            // Deliberate observable demonstration, not a simulated provider latency benchmark.
            try { Thread.sleep(40); }
            catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new PluginCancellation.Cancelled(); }
        }
        emit(frame("","stop",false),cancellation,receiver);
        emit("data: [DONE]\r\n\r\n".getBytes(StandardCharsets.UTF_8),cancellation,receiver);
    }
    private static String reply(OpenAiChatCodec.Request request) {
        String source=request.source;
        if(source.length()>512) {
            int end=512; if(Character.isHighSurrogate(source.charAt(end-1))) end--;
            source=source.substring(0,end)+"…";
        }
        String label="【本机 AI Mock · ";
        switch(request.pluginID) {
            case "ask":return label+"快问】\n这是固定模拟回复，用于验证 OpenAI 兼容流式协议；没有调用 AI 模型。\n问题："+source;
            case "polish":return label+"润色】\n原文样例（未执行真实润色）：\n"+source;
            case "poem":return label+"作诗】\n主题："+source+"\n以下为固定演示诗，不是模型生成：\n清风轻过窗前叶，\n微光慢落案边书。\n一行新字藏心意，\n留待明朝再细读。";
            case "art":return label+"画画】\n纯文本提示词示例："+source+"\n这是字符画流程的本机模拟，没有生成图片。";
            default:throw new IllegalStateException("Validated mock plugin missing");
        }
    }
    private static byte[] frame(String content,String reason,boolean role) throws OpenAiChatCodec.Failure {
        try {
            JSONObject delta=new JSONObject();
            if(role) delta.put("role","assistant");
            if(!content.isEmpty() || role) delta.put("content",content);
            JSONObject choice=new JSONObject().put("index",0).put("delta",delta)
                    .put("finish_reason",reason==null?JSONObject.NULL:reason);
            JSONObject chunk=new JSONObject().put("id","chatcmpl-rimes-local-mock").put("object","chat.completion.chunk")
                    .put("created",0).put("model",OpenAiChatCodec.MOCK_MODEL).put("choices",new JSONArray().put(choice));
            return ("data: "+chunk+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        } catch(JSONException error) { throw new OpenAiChatCodec.Failure(OpenAiChatCodec.Code.INVALID_FRAME,"模拟流式回复格式无效，原文已保留。"); }
    }
    private static void emit(byte[] frame,PluginCancellation cancellation,Receiver receiver) throws OpenAiChatCodec.Failure {
        // Deliberately split frames inside both JSON tokens and multibyte UTF-8 characters.
        int[] fragments={1,2,7,23,61}; int position=0,index=0;
        while(position<frame.length) {
            cancellation.check(); int length=Math.min(fragments[index++%fragments.length],frame.length-position);
            receiver.onBytes(frame,position,length); position+=length;
        }
    }
}
