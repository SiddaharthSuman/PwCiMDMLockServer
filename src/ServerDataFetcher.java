import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.Gson;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ServerDataFetcher implements Runnable {

	public static int WAIT_PERIOD = 20;
	public static final MediaType MEDIA_TYPE_MARKDOWN = MediaType.parse("application/json");

	boolean shutdownFlag = false;

	ReentrantLock lock;
	static LocalDateTime lastFetch;
	String[] devices = { "PwC IPad Mini i20", "PwC IPhone i13", "PwC IPhone i14", "PwC IPhone i9", "PwC IPhone i8",
			"PwC IPhone i7", "PwC IPhone i6", "PwC IPhone i10" };

	OkHttpClient client;

	public ServerDataFetcher(ReentrantLock lock) {
		this.lock = lock;
	}

	// Implement server side as well
	void fetchNewDevices() {
		try {
			Gson gson = new Gson();
			PostMethod method = new PostMethod();
			method.method = "getDeviceListForLocking";
			String methodBody = gson.toJson(method);
			// System.out.println("method body: " + methodBody);
			client = new OkHttpClient();
			Request request = new Request.Builder().url("http://pwcimdm-server.000webhostapp.com/admin/admin.php")
					.post(RequestBody.create(MEDIA_TYPE_MARKDOWN, methodBody)).build();
			Response response = client.newCall(request).execute();
			DevicesGson devicesData;
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response);
			else {
				String devicesJsonString = response.body().string();
				System.out.println(devicesJsonString);

				devicesData = gson.fromJson(devicesJsonString, DevicesGson.class);
			}
			lock.lock();
			TasksRunner.deviceList = new LinkedList<String>(Arrays.asList(devicesData.devices));
			WAIT_PERIOD = devicesData.timeout;
			shutdownFlag = devicesData.shutdown;

		} catch (Exception e) {
			System.out.println("There was an exception while fetching devices from server!");
			e.printStackTrace();
		} finally {
			lastFetch = LocalDateTime.now();
			try {
				lock.unlock();
			} catch (IllegalMonitorStateException e) {
				System.out.println("This thread did not hold the log!");
			}
		}
	}

	@Override
	public void run() {
		// TODO: Fetch devices
		// TODO: Start the time of fetch
		// TODO: Then, calculate to the next thread sleep
		// TODO: REDO
		lastFetch = LocalDateTime.now();
		try {
			do {
				System.out.println("Rerunning this fetcher");

				Thread.sleep(WAIT_PERIOD * 1000);
				fetchNewDevices();

			} while (!shutdownFlag);
			lock.lock();
			TasksRunner.endOfTasks = true;
			lock.unlock();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
