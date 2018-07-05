import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.openqa.selenium.Point;

public class TasksRunner {

	static List<Point> quadrants;
	static int windowWidth;
	static int windowHeight;
	static List<String> deviceList;
	static ExecutorService service;
	static boolean endOfTasks;
	private static boolean tasksStarted;

	public static void main(String[] args) {

		ReentrantLock lock = new ReentrantLock();
		runDeviceFetchSchedule(lock);
		// service = Executors.newFixedThreadPool(4);

		do {

			if (!tasksStarted) {
				System.out.println("Entering for loop");
				getScreenDimensions();
				service = Executors.newFixedThreadPool(4);
				for (int i = 0; i < 4; i++) {
					BrowserTasks task = new BrowserTasks(lock);
					service.execute(task);
				}
				service.shutdown();
			}

			// Stop tasksStarted when service is complete, which means endOfTasks will be
			// true
			tasksStarted = !service.isTerminated();

			while (endOfTasks) {
				// wait for it to be false again
				try {
					Thread.sleep(ServerDataFetcher.WAIT_PERIOD * 1000);
					System.out.println("Waiting for shutdown to be false and sleeping for "
							+ ServerDataFetcher.WAIT_PERIOD + " seconds");
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} while (true);
	}

	static void runDeviceFetchSchedule(ReentrantLock lock) {
		ExecutorService fetcherService = Executors.newSingleThreadExecutor();
		ServerDataFetcher fetcher = new ServerDataFetcher(lock);
		fetcherService.execute(fetcher);
		fetcherService.shutdown();
	}

	static void getScreenDimensions() {
		java.awt.Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		System.out.println(screenSize.width + " width");
		System.out.println(screenSize.height + " height");
		screenSize.height = screenSize.height - 140;
		screenSize.width = screenSize.width - 800;

		windowWidth = screenSize.width / 2;
		windowHeight = screenSize.height / 2;
		int increments = screenSize.width / 3;

		// Divide in 4 quadrants
		quadrants = new ArrayList<Point>();
		quadrants.add(new Point(0, 10));
		quadrants.add(new Point(increments, 10));
		quadrants.add(new Point(increments * 2, 10));
		quadrants.add(new Point(increments * 3, 10));
	}
}
