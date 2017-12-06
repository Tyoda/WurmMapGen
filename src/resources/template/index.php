<!DOCTYPE html>
<html>
<head>
	<title>WU MapGen</title>
	<meta charset="utf-8"/>
	<meta name="viewport" content="initial-scale=1.0, user-scalable=no"/>

	<link rel="stylesheet" href="https://unpkg.com/leaflet@1.2.0/dist/leaflet.css" integrity="sha384-vE+16m4967W2kZS6iZkYOqJ4lwBlnVTEkYhdn4pC+hehUp4OSRYDA9Luwc3FLGW5" crossorigin="anonymous">
	<link rel="stylesheet" href="dist/Leaflet.Coordinates-0.1.5.css">
	<link rel="stylesheet" href="style.css">

	<script src="https://unpkg.com/leaflet@1.2.0/dist/leaflet.js" integrity="sha384-ic4t376ZLdSPIUOxDpNXqU0UWe1c/2BkOXxiirQWAgKZzMJ6njfS1jdzbyMJbfil" crossorigin="anonymous"></script>
	<script type="text/javascript" src="dist/Leaflet.Coordinates-0.1.5.min.js"></script>

	<script type="text/javascript" src="includes/config.js"></script>
	<script type="text/javascript" src="includes/markers.js"></script>
	<script type="text/javascript" src="includes/deeds.js"></script>
	<script type="text/javascript" src="includes/players.php"></script>
	<script type="text/javascript" src="includes/structures.php"></script>
	<script type="text/javascript" src="includes/guardtowers.php"></script>

	<script type="text/javascript" src="app.js"></script>
</head>
<body onload="init()">
<div id="map"></div>
</body>
</html>