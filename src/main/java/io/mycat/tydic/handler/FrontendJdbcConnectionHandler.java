package io.mycat.tydic.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.backend.mysql.ByteUtil;
import io.mycat.net.AbstractConnection;
import io.mycat.net.NIOHandler;
import io.mycat.net.mysql.EOFPacket;
import io.mycat.net.mysql.ErrorPacket;
import io.mycat.net.mysql.FieldPacket;
import io.mycat.net.mysql.OkPacket;
import io.mycat.net.mysql.RequestFilePacket;

public class FrontendJdbcConnectionHandler implements NIOHandler {
	private static final Logger logger = LoggerFactory.getLogger(FrontendJdbcConnectionHandler.class);
	private static final int RESULT_STATUS_INIT = 0;
	private static final int RESULT_STATUS_HEADER = 1;
	private static final int RESULT_STATUS_FIELD_EOF = 2;

	private final AbstractConnection source;

	private volatile int resultStatus;
	private volatile byte[] header;
	//private volatile List<byte[]> fields;
	private volatile List<FieldPacket> fieldPacketList;

	private AtomicInteger ai = new AtomicInteger(0);

	public FrontendJdbcConnectionHandler(AbstractConnection source) {
		this.source = source;
		this.resultStatus = RESULT_STATUS_INIT;
	}

	public AbstractConnection getSource() {
		return source;
	}

	protected void offerDataError() {
		resultStatus = RESULT_STATUS_INIT;
		throw new RuntimeException("offer data error!");
	}

	@Override
	public void handle(byte[] data) {
		switch (resultStatus) {
		case RESULT_STATUS_INIT:
			switch (data[4]) {
			case OkPacket.FIELD_COUNT:
				handleOkPacket(data);
				break;
			case ErrorPacket.FIELD_COUNT:
				handleErrorPacket(data);
				break;
			case RequestFilePacket.FIELD_COUNT:
				handleRequestPacket(data);
				break;
			default:
				resultStatus = RESULT_STATUS_HEADER;
				header = data;
				fieldPacketList = new ArrayList<FieldPacket>((int) ByteUtil.readLength(data, 4));
			}
			break;
		case RESULT_STATUS_HEADER:
			switch (data[4]) {
			case ErrorPacket.FIELD_COUNT:
				resultStatus = RESULT_STATUS_INIT;
				handleErrorPacket(data);
				break;
			case EOFPacket.FIELD_COUNT:
				resultStatus = RESULT_STATUS_FIELD_EOF;
				handleFieldEofPacket(data);
				break;
			default:
				handleFieldPacket(data);
			}
			break;
		case RESULT_STATUS_FIELD_EOF:
			switch (data[4]) {
			case ErrorPacket.FIELD_COUNT:
				resultStatus = RESULT_STATUS_INIT;
				handleErrorPacket(data);
				break;
			case EOFPacket.FIELD_COUNT:
				resultStatus = RESULT_STATUS_INIT;
				handleRowEofPacket(data);
				break;
			default:
				handleRowPacket(data);
			}
			break;
		default:
			throw new RuntimeException("unknown status!");
		}
	}

	private void handleFieldPacket(byte[] data) {
		FieldPacket fieldPacket = new FieldPacket();
		fieldPacket.read(data);
		fieldPacketList.add(fieldPacket);
	}

	/**
	 * OK数据包处理
	 */
	private void handleOkPacket(byte[] data) {
		OkPacket ok = new OkPacket();
		ok.read(data);
		System.out.println("packetLength:" + ok.packetLength + ",packetId:" + ok.packetId + ",fieldCount:"
				+ ok.fieldCount + ",affectedRows:" + ok.affectedRows + ",insertId:" + ok.insertId + ",serverStatus:"
				+ ok.serverStatus + ",warningCount:" + ok.warningCount + ",message:" + new String(ok.message));

	}

	/**
	 * ERROR数据包处理
	 */
	private void handleErrorPacket(byte[] data) {
		System.out.println("handleErrorPacket");
	}

	/**
	 * load data file 请求文件数据包处理
	 */
	private void handleRequestPacket(byte[] data) {
		System.out.println("handleRequestPacket");
	}

	/**
	 * 字段数据包结束处理
	 */
	private void handleFieldEofPacket(byte[] data) {
		System.out.println("handleFieldEofPacket");
	}

	/**
	 * 行数据包处理
	 */
	private void handleRowPacket(byte[] data) {
		//System.out.println(new String(data));
		System.out.println(ai.incrementAndGet());
	}

	/**
	 * 行数据包结束处理
	 */
	private void handleRowEofPacket(byte[] data) {
		System.out.println("handleRowEofPacket");
		System.out.println(new String(data));
		for (FieldPacket packet : fieldPacketList) {
			System.out.println(packet.toString());
			System.out.println("====================");
		}
	}

	private void closeNoHandler() {
		if (!source.isClosed()) {
			source.close("no handler");
			logger.warn("no handler bind in this con " + this + " client:" + source);
		}
	}

}
