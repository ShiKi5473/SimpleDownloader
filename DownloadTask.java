package jDownloader;


import java.io.File;

import java.io.FileOutputStream;
import java.io.InputStream;

import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

public class DownloadTask extends SwingWorker<Void, Long>{
	private String url;
	private JDownloadUI ui;
	private long MEMORY_THRESHOLD = 50 * 1024 * 1024, totalFileSize;
	private final File tempSavePath, savePath;
	
	public DownloadTask(String url, JDownloadUI ui, long totalFileSize, File tempSaveLocation, File saveLocation) {
		this.url = url;
		this.ui = ui;
		this.totalFileSize = totalFileSize;
		this.tempSavePath = tempSaveLocation;
		this.savePath = saveLocation;
	}
	
	private static final HttpClient client  = HttpClient.newBuilder()
												.followRedirects(Redirect.NORMAL)
												.connectTimeout(Duration.ofSeconds(20))
												.build();

	@Override
	protected Void doInBackground() throws Exception {
		
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET()
//				.timeout(Duration.ofMinutes(30))
				.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36")
				.build();
		
		try {
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			
			if(response.statusCode() == 200) {
				System.out.printf("檔案總大小 %d KB\n", (totalFileSize/1024));
				saveFile(response.body());
			}else {
				throw new Exception("伺服器回應錯誤： " + response.statusCode());
			}
			
			
		} catch (Exception e) {
			
			throw e;
		}
		return null;
		
	}
	@Override
	protected void process(List<Long> chunks) {
		Long downloadedKB = chunks.get(chunks.size()-1);
		ui.updateStatus(String.format("已下載%dKB/%dKB", downloadedKB, (totalFileSize/1024)));	
	}
	@Override
	protected void done() {
		try {
			get();
			
			Files.move(tempSavePath.toPath(), savePath.toPath(), StandardCopyOption.REPLACE_EXISTING);
			ui.updateStatus("檔案已成功儲存！");
			JOptionPane.showMessageDialog(ui, "已成功儲存到：\n" + savePath.getAbsolutePath());
		} catch (Exception e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			ui.updateStatus("下載失敗");
			JOptionPane.showMessageDialog(ui, cause.getMessage(), "錯誤", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}finally {
			if(tempSavePath.exists()) {
				tempSavePath.delete();
			}
			ui.setUIEnabled(true);
		}
	}
	
	
	private void saveFile(InputStream inputStream) throws Exception {
		try (InputStream is = inputStream;
				FileOutputStream fos = new FileOutputStream(tempSavePath)){
			long downloadByte = 0;
			byte[] buffer = new byte[1024*8];
			int bytesRead;
			while((bytesRead = is.read(buffer)) != -1) {
				fos.write(buffer, 0, bytesRead);
				downloadByte += bytesRead;
				if(totalFileSize > 0) {
					int progress = (int) ((downloadByte * 100) / totalFileSize);
					setProgress(progress);
				}
				publish((downloadByte / 1024));
			}
			System.out.println("下載完成");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	


}
