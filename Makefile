build:
	mvn clean install
	cd mover && mvn assembly:assembly
	