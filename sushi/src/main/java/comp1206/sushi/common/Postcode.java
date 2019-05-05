package comp1206.sushi.common;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import org.json.*;

import javax.swing.*;

public class Postcode extends Model implements Serializable {

	private String name;
	private Map<String, Double> latLong;
	private Number distance;

	public Postcode(String code) {
		this.name = code;
		calculateLatLong();
		this.distance = Integer.valueOf(0);
	}

	public Postcode(String code, Restaurant restaurant) {
		this.name = code;
		calculateLatLong();
		this.distance = calculateDistance(restaurant.getLocation());
	}

	@Override
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Number getDistance() {
		return this.distance;
	}

	public Map<String,Double> getLatLong() {
		return this.latLong;
	}

	public double calculateDistance(Postcode destination)
	{
		// *******************************************************************************************************************************
		// * Title: Adapted lat/long distance calculator method
		// * Author: David George
		// * Date: 28/05/2013
		// * Availability: https://stackoverflow.com/questions/3694380/calculating-distance-between-two-points-using-latitude-longitude/
		// ******************************************************************************************************************************/
		final int R = 6371; // Radius of the earth

		double lat1 = destination.latLong.get("lat");
		double lon1 = destination.latLong.get("long");

		double lat2 = latLong.get("lat");
		double lon2 = latLong.get("long");

		double latDistance = Math.toRadians(lat2 - lat1);
		double lonDistance = Math.toRadians(lon2 - lon1);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double distance = R * c * 1000; // convert to meters

		distance = Math.pow(distance, 2);

		return Double.parseDouble(String.format("%.2f", Math.sqrt(distance)));
	}

	private void calculateLatLong()
	{
		this.latLong = new HashMap<>();

		try
		{
			URL requestURL = new URL("https://www.southampton.ac.uk/~ob1a12/postcode/postcode.php?postcode=" + name.replace(" ", ""));
			URLConnection connection = requestURL.openConnection();
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

			JSONObject jsonPostcode = new JSONObject(in.readLine());

			latLong.put("lat", Double.parseDouble(String.format("%.6f", Double.parseDouble(jsonPostcode.get("lat").toString()))));
			latLong.put("long", Double.parseDouble(String.format("%.6f", Double.parseDouble(jsonPostcode.get("long").toString()))));
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(new JDialog(), "Error: " + ex.getMessage() + "\r\nThis likely means that the entered postcode is invalid. Please ensure that your input is correct and try again.", "Input Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
	}
}