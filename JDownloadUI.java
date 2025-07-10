package jDownloader;

import java.awt.BorderLayout;

import java.awt.Font;

import java.io.File;


import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;



public class JDownloadUI extends JFrame{

	private JTextField  inputURL;
	private JButton get;
//	private JPanel inputPanel;
	private JProgressBar progressBar;
	private JLabel statusLabel;
	private String url;
//	private String savePath;
	

	
	public JDownloadUI() {
		setLayout(new BorderLayout(10, 10));
		JPanel contentPanel = (JPanel) getContentPane();
		contentPanel.setBorder(new EmptyBorder(10,10,10,10));
		
		JPanel top = new JPanel(new BorderLayout(5,5));

		
		inputURL = new JTextField(100);
		get = new JButton("Get");
		
		JPanel center = new JPanel(new BorderLayout());
		
		
//		get.setPreferredSize(new Dimension(100, 30));
		top.add(new JLabel("Enter URL："), BorderLayout.WEST);
		top.add(inputURL, BorderLayout.CENTER);
		top.add(get, BorderLayout.EAST);
		contentPanel.add(top, BorderLayout.NORTH);

		JPanel bottom = new JPanel(new BorderLayout(5,5));
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		statusLabel = new JLabel("請輸入url並開始下載");
		statusLabel.setFont(new Font("微軟正黑體", Font.PLAIN, 12));
		bottom.add(progressBar, BorderLayout.CENTER);
		bottom.add(statusLabel, BorderLayout.SOUTH);
//		add(bottom, BorderLayout.CENTER);
//		add(top, BorderLayout.NORTH);
//		add(center, BorderLayout.CENTER);
		contentPanel.add(bottom, BorderLayout.CENTER);

		get.addActionListener(e -> getURL());
		
		pack();
//		setSize(1280, 720);
		setVisible(true);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		

	}
	
	private void getURL() {
		this.url = inputURL.getText().trim();
		if(url.isEmpty()) {
			JOptionPane.showMessageDialog(this, "請輸入正確的url", "錯誤", JOptionPane.ERROR_MESSAGE);
			return;
		}
		get.setEnabled(false);
		inputURL.setEditable(false);
		progressBar.setValue(0);
		statusLabel.setText("正在檢查連線...");
		
		ConnectChecker connChecker = new ConnectChecker(url, this);
		connChecker.execute();
		
		
		
	}
	
	protected void startDownload(ConnectChecker.DownLoadInfo info) {
		JFileChooser fileChooser = createFileChooser(info.suggestFileName);
		int userSelection = fileChooser.showSaveDialog(this);
		if(userSelection == JFileChooser.APPROVE_OPTION) {
			File saveLocation = fileChooser.getSelectedFile();
			 File tempSaveLocation = new File(saveLocation.getAbsolutePath() + ".downloading"); // 這是我們的暫存檔
			if(saveLocation.exists()) {
				int response = JOptionPane.showConfirmDialog(this, "檔案已存在，是否覆蓋？", "確認儲存", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
				if(response != JOptionPane.YES_OPTION) {
					updateStatus("使用者取消操作");
					setUIEnabled(true);
					return;
				}
			}
			
            if (tempSaveLocation.exists()) {
                tempSaveLocation.delete();
            }
			
			SwingWorker<?, ?> task;
			if(info.supportsRange && info.fileSize > 0) {
				updateStatus("伺服器支援分塊下載，啟用多執行緒模式。");
				task = new MultiThreadDownloadTask(url, this, info.fileSize, tempSaveLocation, saveLocation);
			}else {
				updateStatus("伺服器不支援分塊下載，使用單執行緒模式。");
                task = new DownloadTask(url, this, info.fileSize, tempSaveLocation, saveLocation);
			}

			task.addPropertyChangeListener(evt -> {
				if("progress".equals(evt.getPropertyName())) {
					progressBar.setValue((Integer)evt.getNewValue());
				}
			});
			task.execute();
			
		}else {
			updateStatus("使用者取消操作");
			setUIEnabled(true);
		}
		
	}
	
	
	public void updateStatus(String text) {
		statusLabel.setText(text);
	}
    public void setUIEnabled(boolean enabled) {
        get.setEnabled(enabled);
        inputURL.setEditable(enabled);
    }
    
    
	private JFileChooser createFileChooser(String suggestedName) {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("儲存檔案");
		if(suggestedName != null && !suggestedName.isEmpty()) {
			fileChooser.setSelectedFile(new File(suggestedName));
		}
		return fileChooser;
	}

	public static void main(String[] args) {
		 javax.swing.SwingUtilities.invokeLater(JDownloadUI::new);

	}
	

}
