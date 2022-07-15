FROM ruby:3.1.2-slim

ADD ./config.rb /etc/riemann-dash.config.rb

RUN apt-get update && \
    apt-get install -y --no-install-recommends make gcc g++ && \
    rm -rf /var/lib/apt/lists/*

RUN gem install thin riemann-dash

EXPOSE 4567

CMD ["riemann-dash", "/etc/riemann-dash.config.rb"]
