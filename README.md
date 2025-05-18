# Introduction To Kafka Streams App
1. Place your dataset to root folder.
2. In config.py file set DATASET_PATH = "your_file_name"
3. Run docker compose up -d 
4. Run docker compose logs -f counter-app
5. Wait for messages to arrive and counter returns top 5 root domains.