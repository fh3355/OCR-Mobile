# Step 1: Use a newer, officially supported Python runtime as a parent image.
FROM python:3.9-slim-bullseye

# Step 2: Set the working directory inside the container
WORKDIR /home

# Step 3: Change APT source to a mirror for faster downloads
RUN sed -i 's/deb.debian.org/mirrors.tuna.tsinghua.edu.cn/g' /etc/apt/sources.list && \
    sed -i 's|security.debian.org/debian-security|mirrors.tuna.tsinghua.edu.cn/debian-security|g' /etc/apt/sources.list

# Step 4: Install system-level dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1-mesa-glx \
    libglib2.0-0 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Step 5: Install the CPU-compatible version of PaddlePaddle directly.
# Using version 2.6.2 as 2.6.1 is no longer available from the official source.
RUN pip install --no-cache-dir paddlepaddle==2.6.2 -f https://www.paddlepaddle.org.cn/whl/linux/cpu-noavx/develop.html -i https://pypi.tuna.tsinghua.edu.cn/simple

# Step 6: Copy the rest of the requirements and install them from a fast mirror.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# Step 7: Inform Docker that the container listens on port 5000
EXPOSE 5000

# Step 8: Keep the container running to provide the environment.
CMD ["tail", "-f", "/dev/null"]
