"""Writes the deterministic 100-product seed CSV loaded by Liquibase changeset 1.4.0-002-seed-products.

Usage: python3 scripts/generate_products_csv.py > src/main/resources/db/changelog/releases/1.4.0/products.csv
"""

import csv
import random
import sys
import uuid
from datetime import datetime, timedelta

NAMESPACE = uuid.UUID("7d0f6a3e-4c55-4b8e-9a51-1f6c2b0e9d10")
CATALOG = {
    "electronics": ["Wireless Headphones", "Bluetooth Speaker", "USB-C Charger", "Smart Watch", "Mechanical Keyboard"],
    "books": ["Java Concurrency Guide", "Spring in Practice", "Database Internals", "Clean Architecture", "Domain Modeling"],
    "home": ["Ceramic Mug", "Desk Lamp", "Throw Pillow", "Wall Clock", "Storage Basket"],
    "sports": ["Yoga Mat", "Running Shoes", "Water Bottle", "Resistance Bands", "Tennis Racket"],
    "toys": ["Building Blocks", "Puzzle Set", "Remote Car", "Plush Bear", "Board Game"],
    "beauty": ["Face Serum", "Hand Cream", "Lip Balm", "Shampoo Bar", "Sunscreen"],
}
COLOURS = ["Red", "Blue", "Black", "White", "Green"]


def rows(count=100):
    rnd = random.Random(42)
    start = datetime(2026, 1, 1, 9, 0, 0)
    categories = list(CATALOG)
    for i in range(count):
        category = categories[i % len(categories)]
        name = f"{rnd.choice(COLOURS)} {rnd.choice(CATALOG[category])} {i + 1:03d}"
        stock = 0 if i % 10 == 3 else rnd.randint(1, 500)
        created = (start + timedelta(hours=i)).strftime("%Y-%m-%dT%H:%M:%S")
        yield [
            uuid.uuid5(NAMESPACE, str(i)),
            name,
            category,
            rnd.randint(199, 199_999),
            stock,
            round(rnd.uniform(1.0, 5.0), 1),
            created,
            created,
        ]


writer = csv.writer(sys.stdout, lineterminator="\n")
writer.writerow(["id", "name", "category", "price_cents", "stock", "rating", "created_at", "updated_at"])
writer.writerows(rows())
