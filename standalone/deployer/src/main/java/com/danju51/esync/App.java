package com.danju51.esync;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.otter.manager.biz.config.channel.ChannelService;
import com.alibaba.otter.manager.biz.remote.ConfigRemoteService;
import com.alibaba.otter.node.etl.OtterConstants;
import com.alibaba.otter.node.etl.OtterContextLocator;
import com.alibaba.otter.node.etl.OtterController;
import com.alibaba.otter.shared.arbitrate.impl.config.ArbitrateConfigRegistry;
import com.alibaba.otter.shared.common.model.config.ConfigException;
import com.alibaba.otter.shared.common.model.config.channel.Channel;
import com.alibaba.otter.shared.common.model.config.channel.ChannelStatus;

/**
 * Hello world!
 *
 */
public class App 
{
	 private static final Logger logger = LoggerFactory.getLogger(App.class);

	    public static void main(String[] args) throws Throwable {
	        // 启动dragoon client
	        // startDragoon();
	        // logger.info("INFO ## the dragoon is start now ......");
	    	
	    	OtterContextLocator.getBean("configRemoteService");

	    	
	    	
	        final OtterController controller = OtterContextLocator.getOtterController();
	        controller.start();
	        
	        try {
	            logger.info("INFO ## the otter server is running now ......");
	            Runtime.getRuntime().addShutdownHook(new Thread() {

	                public void run() {
	                    try {
	                        logger.info("INFO ## stop the otter server");
	                        controller.stop();
	                    } catch (Throwable e) {
	                        logger.warn("WARN ##something goes wrong when stopping Otter Server:\n{}",
	                            ExceptionUtils.getFullStackTrace(e));
	                    } finally {
	                        logger.info("INFO ## otter server is down.");
	                    }
	                }

	            });
	        } catch (Throwable e) {
	            logger.error("ERROR ## Something goes wrong when starting up the Otter Server:\n{}",
	                ExceptionUtils.getFullStackTrace(e));
	            System.exit(0);
	        }
	        
	     // 获取一下nChannel变量
	        String nid = System.getProperty(OtterConstants.START_CHANNEL);
	        if (!StringUtils.isEmpty(nid)) { 	 
		        logger.info("INFO ## start notify channel tanks..");
		       
		        final ChannelService channelService=OtterContextLocator.getBean("channelService");
		        if("all".equalsIgnoreCase(nid)){
		        	for(Long id:channelService.listAllChannelId())
		        		channelService.startChannel(id);
		        }else
		           channelService.startChannel(Long.valueOf(nid));
	        }
	        
	    }
}
