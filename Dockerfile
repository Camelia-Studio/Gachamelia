FROM node:20

# Create app directory
WORKDIR /app

# Install app dependencies
COPY package.json /app
COPY package-lock.json /app

RUN npm install

# Bundle app source
COPY . /app

CMD [ "npm", "run", "start:clean" ]
