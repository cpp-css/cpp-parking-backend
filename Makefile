#change these as necessary when you build your own copy
export IMAGE_PREFIX = calpolypomona
export IMAGE_NAME = cpp-parking-backend
export TAG = latest

.PHONY: clean run build remove push

build:
	bin/activator dist
	docker build -t=$(IMAGE_PREFIX)/$(IMAGE_NAME):$(TAG) .

net:
	docker network ls | grep cpp-parking-net || docker network create cpp-parking-net

run: remove net
	#docker run --name=$(IMAGE_NAME) $(IMAGE_PREFIX)/$(IMAGE_NAME):$(TAG)
	docker-compose up

clean:
	bin/activator clean

remove:
	#(docker stop $(IMAGE_NAME) && docker rm $(IMAGE_NAME)) || :
	docker-compose down

push:
	docker push $(IMAGE_PREFIX)/$(IMAGE_NAME):$(TAG)
