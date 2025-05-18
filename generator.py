import pandas as pd
from kafka import KafkaProducer, KafkaAdminClient
from kafka.admin import NewTopic
import json
import time
import logging
import sys
import config

logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(stream=sys.stdout)
    ]
)

# Configuration
KAFKA_TOPIC = config.KAFKA_TOPIC
KAFKA_BOOTSTRAP_SERVERS = config.KAFKA_BOOTSTRAP_SERVERS
DATASET_PATH = config.DATASET_PATH
NUM_PARTITIONS = config.NUM_PARTITIONS
NUM_REPLICAS = config.NUM_REPLICAS

def create_topic_if_not_exists(topic_name, partitions, replication_factor):
    admin_client = KafkaAdminClient(bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS)
    existing_topics = admin_client.list_topics()

    if topic_name not in existing_topics:
        topic = NewTopic(name=topic_name, num_partitions=partitions, replication_factor=replication_factor)
        admin_client.create_topics(new_topics=[topic], validate_only=False)
        logging.info(f"Created topic '{topic_name}' with {partitions} partitions")
    else:
        print(f"Topic '{topic_name}' already exists")

def load_data(file_path):
    """Loads the dataset and yields one comment at a time."""
    chunksize = 1000
    for chunk in pd.read_csv(file_path, usecols=["url"], chunksize=chunksize):
        for url in chunk["url"]:
            yield {"url": url}

def send_comments(producer, topic, comment_generator):
    for comment in comment_generator:
        message = json.dumps(comment).encode('utf-8')
        producer.send(topic, message)
        time.sleep(0.01)

if __name__ == "__main__":
    create_topic_if_not_exists(KAFKA_TOPIC, NUM_PARTITIONS, NUM_REPLICAS)
    producer = KafkaProducer(bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS)
    comment_gen = load_data(DATASET_PATH)
    send_comments(producer, KAFKA_TOPIC, comment_gen)
    producer.flush()
    producer.close(timeout=5)

