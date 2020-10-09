package com.jdq.auth.rest.modular.auth.filter;

import com.jdq.auth.rest.common.CurrentUser;
import com.jdq.auth.rest.common.exception.BizExceptionEnum;
import com.jdq.auth.rest.common.tips.ErrorTip;
import com.jdq.auth.rest.config.properties.JwtPropertise;
import com.jdq.auth.rest.modular.auth.util.JwtTokenUtil;
import com.jdq.auth.rest.modular.auth.util.RenderUtil;
import io.jsonwebtoken.JwtException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 对客户端请求的jwt token验证过滤器
 *
 * @author fengshuonan
 * @Date 2017/8/24 14:04
 */
public class AuthFilter extends OncePerRequestFilter {

    private final Log logger = LogFactory.getLog(this.getClass());

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private JwtPropertise jwtProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request.getServletPath().equals("/" + jwtProperties.getAuthPath())) {
            chain.doFilter(request, response);
            return;
        }

        // 配置忽略列表
        String ignoreUrl = jwtProperties.getIgnoreUrl();
        logger.info("当前访问的url: " + request.getServletPath());
        String[] ignoreUrls = ignoreUrl.split(",");
        for(int i=0;i<ignoreUrls.length;i++){
            if(request.getServletPath().startsWith(ignoreUrls[i])){
                chain.doFilter(request, response);
                return;
            }
        }


        final String requestHeader = request.getHeader(jwtProperties.getHeader());
        String authToken = null;
        if (requestHeader != null && requestHeader.startsWith("Bearer ")) {
            authToken = requestHeader.substring(7);
            // 通过Token获取userID，并且将之存入Threadlocal，以便后续业务调用
            String userId = jwtTokenUtil.getUsernameFromToken(authToken);
            if(userId == null){
                return;
            } else {
                CurrentUser.saveUserId(userId);
            }

            //验证token是否过期,包含了验证jwt是否正确
            try {
                boolean flag = jwtTokenUtil.isTokenExpired(authToken);
                if (flag) {
                    RenderUtil.renderJson(response, new ErrorTip(BizExceptionEnum.TOKEN_EXPIRED.getCode(), BizExceptionEnum.TOKEN_EXPIRED.getMessage()));
                    return;
                }
            } catch (JwtException e) {
                //有异常就是token解析失败
                RenderUtil.renderJson(response, new ErrorTip(BizExceptionEnum.TOKEN_ERROR.getCode(), BizExceptionEnum.TOKEN_ERROR.getMessage()));
                return;
            }
        } else {
            //header没有带Bearer字段
            RenderUtil.renderJson(response, new ErrorTip(BizExceptionEnum.TOKEN_ERROR.getCode(), BizExceptionEnum.TOKEN_ERROR.getMessage()));
            return;
        }
        chain.doFilter(request, response);
    }
}