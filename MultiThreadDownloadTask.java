package jDownloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

public class MultiThreadDownloadTask extends SwingWorker<Void, Long> {
	private final String url;
	private final JDownloadUI ui;
	private final long totalFileSize;
	private final File savePath, tempSavePath;
	private final int THREAD_COUNT = 8;
	private final int MAX_RETRIES = 3;
	private final List<File> tempFiles = new ArrayList<>();
	private final AtomicLong totalBytesDownloaded = new AtomicLong(0L);
	
	private static final HttpClient client = HttpClient.newBuilder()
			.followRedirects(Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(20))
			.build();
	
	private static class Chunk{
		final long startByte;
		final long endByte;
		final File tempFile;
		int retryCount = 0;
		
		Chunk(long startByte, long endByte, File tempFile){
			this.startByte = startByte;
			this.endByte = endByte;
			this.tempFile = tempFile;
		}
	}
	
	public MultiThreadDownloadTask(String url, JDownloadUI ui, long totalFileSize, File tempSavePath, File savePath) {
		this.url = url;
		this.ui = ui;
		this.totalFileSize = totalFileSize;
		this.tempSavePath = tempSavePath;
		this.savePath = savePath;
	}
	
	@Override
	protected Void doInBackground() throws Exception{
		final ConcurrentLinkedQueue<Chunk> chunkQueue = new ConcurrentLinkedQueue<>();
		long chunkSize = totalFileSize / THREAD_COUNT;
		for(int i = 0; i < THREAD_COUNT; i++) {
			long startByte = i * chunkSize;
			long endByte = (i == THREAD_COUNT - 1) ? totalFileSize-1 : startByte + chunkSize - 1;
			
			File tempFile = File.createTempFile(savePath.getName() + ".part" + i,  ".tmp");
			tempFiles.add(tempFile);
			chunkQueue.add(new Chunk(startByte, endByte, tempFile));
		}
		
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		List<Callable<Void>> workers = new ArrayList<Callable<Void>>();
		for(int i = 0; i < THREAD_COUNT; i++) {
			workers.add(createWorker(chunkQueue));
		}
		
		try {
			List<Future<Void>> futures = executor.invokeAll(workers);
			
			for(Future<Void> future : futures) {
				future.get();
			}
			
			combineChunks();
			
		}catch (Exception e) {
			executor.shutdownNow();
			cleanTempFiles();
			throw e;
		}finally {
			executor.shutdown();
		}
		return null;
	}
	
	private Callable<Void> createWorker(final ConcurrentLinkedQueue<Chunk> queue){
		return () -> {
			Chunk chunk;
			while((chunk = queue.poll()) != null) {
				try {
					downloadChunk(chunk);
				} catch (Exception e) {
					System.err.println("區塊下載失敗，準備重試。起始位元組: " + chunk.startByte + ". 錯誤: " + e.getMessage());
					if(chunk.retryCount < MAX_RETRIES) {
						chunk.retryCount++;
						queue.add(chunk);
						Thread.sleep(500);
					}else {
						throw new IOException("區塊下載已達最大重試次數，任務失敗。起始位元組: " + chunk.startByte, e);
					}
				}
			}
			return null;
		};
	}
	
	private void downloadChunk(Chunk chunk) throws Exception{
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Range", "bytes=" + chunk.startByte + "-" + chunk.endByte)
				.GET()
				.build();
		
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		
		if(response.statusCode() != 206) {
			throw new Exception("伺服器回應錯誤 (非 206): " + response.statusCode());
		}
		try (InputStream in = response.body();
				FileOutputStream fos = new FileOutputStream(chunk.tempFile)){
			byte[] buffer = new byte[8*1024];
			int bytesRead;
			while((bytesRead = in.read(buffer)) != -1) {
				fos.write(buffer, 0, bytesRead);
				long downloaded = totalBytesDownloaded.addAndGet(bytesRead);
				
				int progress = (int) ((downloaded *100)/ totalFileSize);
				setProgress(progress);
				publish(downloaded/1024);
				
			}
		}
	}
	
	private void combineChunks() throws Exception{
		ui.updateStatus("正在合併檔案...");
		try (FileOutputStream fos = new FileOutputStream(tempSavePath)){
			for(File tempFile: tempFiles) {
				try (FileInputStream fis = new FileInputStream(tempFile);){
					fis.transferTo(fos);
				} 
			}
			
		} finally {
			cleanTempFiles();
		}
	}
	private void cleanTempFiles() {
		for(File tempfile: tempFiles) {
			if(tempfile.exists()) {
				tempfile.delete();
			}
		}
	}
	@Override
	protected void process(List<Long> chunks) {
		Long downloadedKB = chunks.get(chunks.size() - 1);
		ui.updateStatus(String.format("已下載 %d KB / %d KB", downloadedKB, (totalFileSize / 1024)));
	}
	
	@Override
	protected void done() {
		try {
			get();
			Files.move(tempSavePath.toPath(), savePath.toPath(), StandardCopyOption.REPLACE_EXISTING);
			ui.updateStatus("檔案已成功儲存！");
            JOptionPane.showMessageDialog(ui, "已成功儲存到：\n" + savePath.getAbsolutePath());
		} catch (Exception e) {
			Throwable cause = e.getCause() != null ? e.getCause():e;
			ui.updateStatus("下載失敗: " + cause.getMessage());
			JOptionPane.showMessageDialog(ui, cause.getMessage(), "錯誤", JOptionPane.ERROR_MESSAGE);
	        e.printStackTrace();
	     } finally {
	       cleanTempFiles();
	       if(tempSavePath.exists()) {
	    	   tempSavePath.delete();
	       }
	       ui.setUIEnabled(true);
	     }
	}
}
