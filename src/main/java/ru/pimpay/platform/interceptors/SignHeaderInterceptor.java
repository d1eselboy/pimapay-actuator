package ru.pimpay.platform.interceptors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.binding.soap.interceptor.SoapPreProtocolOutInterceptor;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.springframework.beans.factory.annotation.Autowired;
import ru.pimpay.platform.sign.SignProvider;

@Slf4j
public class SignHeaderInterceptor extends AbstractPhaseInterceptor<Message> {

    @Autowired
    private SignProvider signProvider;

    public SignHeaderInterceptor() {
        super(Phase.PREPARE_SEND);
        addBefore(SoapPreProtocolOutInterceptor.class.getName());
    }


    public void handleMessage(Message message) {
        boolean isOutbound = false;
        isOutbound = message == message.getExchange().getOutMessage()
            || message == message.getExchange().getOutFaultMessage();

        if (isOutbound) {
            OutputStream os = message.getContent(OutputStream.class);

            CachedStream cs = new CachedStream();
            message.setContent(OutputStream.class, cs);
            message.getInterceptorChain().doIntercept(message);

            String currentEnvelopeMessage;
            try {
                cs.flush();
                IOUtils.closeQuietly(cs);
                CachedOutputStream csnew = (CachedOutputStream) message.getContent(OutputStream.class);

                currentEnvelopeMessage = IOUtils.toString(csnew.getInputStream(), "UTF-8");
                csnew.flush();
                IOUtils.closeQuietly(csnew);

                InputStream replaceInStream = IOUtils.toInputStream(currentEnvelopeMessage, "UTF-8");

                IOUtils.copy(replaceInStream, os);
                replaceInStream.close();
                IOUtils.closeQuietly(replaceInStream);

                os.flush();
                message.setContent(OutputStream.class, os);
                IOUtils.closeQuietly(os);

            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }

            Method method = message.getExchange().get(Method.class);

            if (null != method) {

                Map<String, List<String>> reqHeaders = CastUtils.cast((Map<?, ?>) message.get(Message.PROTOCOL_HEADERS));

                if (reqHeaders == null) {
                    reqHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                }

                if (reqHeaders.size() == 0) {
                    message.put(Message.PROTOCOL_HEADERS, reqHeaders);
                }

                String signedData = signProvider.signData(currentEnvelopeMessage);
                message.getExchange().put("SIGN", signedData);
                reqHeaders.put("Test-header2", Collections.singletonList("header2"));
                //reqHeaders.put("X-Request-Signature", Collections.singletonList(signedData));
            }
        }
    }

    public void handleFault(Message message) {
    }

    private class CachedStream extends CachedOutputStream {
        public CachedStream() {
            super();
        }

        protected void doFlush() throws IOException {
            currentStream.flush();
        }

        protected void doClose() throws IOException {
        }

        protected void onWrite() throws IOException {
        }
    }
}